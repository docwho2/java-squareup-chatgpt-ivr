package cloud.cleo.chimesma.squareup;

import com.squareup.square.Environment;
import com.squareup.square.SquareClient;
import com.squareup.square.api.LocationsApi;
import com.squareup.square.models.BusinessHoursPeriod;
import com.squareup.square.models.Location;
import java.time.DayOfWeek;
import static java.time.DayOfWeek.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Determine whether open or closed based on Square Hours from API call.  Cache and
 * hold last result, so if API is down, we always have a value to return.
 * 
 * @author sjensen
 */
public class SquareHours {

    private final static SquareClient client = new SquareClient.Builder()
            .accessToken(System.getenv("SQUARE_API_KEY"))
            .environment(Environment.PRODUCTION)
            .build();

    private final static LocationsApi locationsApi = client.getLocationsApi();

    private final static String LOCATION_ID = System.getenv("SQUARE_LOCATION_ID");
    
    // Cached location result
    private Location loc;
    private ZonedDateTime loc_last;

    private final static SquareHours me = new SquareHours();

    private SquareHours() {
        getLocation();
    }

    public static SquareHours getInstance() {
        return me;
    }

    /**
     * Load and cache location, can be null if unable to retrieve
     */
    private Location getLocation() {
        final var now = ZonedDateTime.now();
        if (loc != null) {
            // We have a cached location
            if (loc_last.isBefore(now.minusHours(12))) {
                // Cache expired, try to hit API
                loadLocation();

            }
        } else {
            loadLocation();
        }
        return loc;
    }

    private void loadLocation() {
        try {
            final var res = locationsApi.retrieveLocation(LOCATION_ID);
            if (res.getLocation() != null) {
                loc = res.getLocation();
                loc_last = ZonedDateTime.now();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isOpen() {
        if ( getLocation() != null ) {
            return new BusinessHours(loc.getBusinessHours().getPeriods()).isOpen();
        }
        return false;
    }

    private class BusinessHours extends ArrayList<OpenPeriod> {

        public BusinessHours(List<BusinessHoursPeriod> periods) {
            periods.forEach(p -> add(new OpenPeriod(p)));
        }
        
        public boolean isOpen() {
            final var tz = ZoneId.of(loc.getTimezone());
            // The current time in the TZ
            final var now = ZonedDateTime.now(tz);
            final var today = LocalDate.now(tz);
            
            // The Day of Week
            final var dow = now.getDayOfWeek();
            
            final var matched = stream().filter(p -> p.getDow().equals(dow) ).toList();
            
            if ( ! matched.isEmpty() ) {
                // There is something matched for this dow of week
                return matched.stream().anyMatch(p -> {
                    final var start = LocalDateTime.of(today, p.getStart()).atZone(tz);
                    final var end = LocalDateTime.of(today, p.getEnd()).atZone(tz);
                    return now.isAfter(start) && now.isBefore(end);
                });
            }
            // We don't have any entries for today, so definitely closed
            return false;
        }
    }

    @Data
    private class OpenPeriod {

        final DayOfWeek dow;
        final LocalTime start;
        final LocalTime end;

        public OpenPeriod(BusinessHoursPeriod bhp) {
            dow = switch (bhp.getDayOfWeek()) {
                case "SUN" ->
                    SUNDAY;
                case "MON" ->
                    MONDAY;
                case "TUE" ->
                    TUESDAY;
                case "WED" ->
                    WEDNESDAY;
                case "THU" ->
                    THURSDAY;
                case "FRI" ->
                    FRIDAY;
                case "SAT" ->
                    SATURDAY;
                default ->
                    throw new RuntimeException("Day of Week Cannot be matched " + bhp.getDayOfWeek());
            };
            
            start = LocalTime.parse(bhp.getStartLocalTime());
            end = LocalTime.parse(bhp.getEndLocalTime());
        }
    }
}
