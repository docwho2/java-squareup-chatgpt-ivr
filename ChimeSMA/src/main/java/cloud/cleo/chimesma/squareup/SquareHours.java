package cloud.cleo.chimesma.squareup;

import com.squareup.square.Environment;
import com.squareup.square.SquareClient;
import com.squareup.square.api.LocationsApi;
import com.squareup.square.authentication.BearerAuthModel;
import com.squareup.square.models.BusinessHoursPeriod;
import com.squareup.square.models.Location;
import java.time.DayOfWeek;
import static java.time.DayOfWeek.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Data;

/**
 * Determine whether open or closed based on Square Hours from API call. Cache and hold last result, so if API is down,
 * we always have a value to return.
 *
 * @author sjensen
 */
public class SquareHours {

    private final static String SQUARE_LOCATION_ID = System.getenv("SQUARE_LOCATION_ID");
    private final static String SQUARE_API_KEY = System.getenv("SQUARE_API_KEY");

    private final static SquareClient client = new SquareClient.Builder()
            .bearerAuthCredentials(new BearerAuthModel.Builder(SQUARE_API_KEY).build())
            .environment(Environment.valueOf(System.getenv("SQUARE_ENVIRONMENT")))
            .build();

    private final static LocationsApi locationsApi = client.getLocationsApi();

    private final boolean squareEnabled;

    // Cached location result
    private volatile Location loc;
    private volatile ZoneId tz;
    private volatile ZonedDateTime loc_last;

    // Lock for synchronizing access to location data
    private final ReentrantLock lock = new ReentrantLock();

    private final static SquareHours me = new SquareHours();

    private SquareHours() {
        // Enabled if we have what looks like key and location set
        squareEnabled = !((SQUARE_LOCATION_ID == null || SQUARE_LOCATION_ID.isBlank() || SQUARE_LOCATION_ID.equalsIgnoreCase("DISABLED")) || (SQUARE_API_KEY == null || SQUARE_API_KEY.isBlank() || SQUARE_API_KEY.equalsIgnoreCase("DISABLED")));
        System.out.println("Square Enabled check = " + squareEnabled);
        if (squareEnabled) {
            getLocation();
        }
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
        lock.lock();
        try {
            final var res = locationsApi.retrieveLocation(SQUARE_LOCATION_ID);
            if (res.getLocation() != null) {
                loc = res.getLocation();
                tz = ZoneId.of(loc.getTimezone());
                loc_last = ZonedDateTime.now();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    /**
     * IS the store currently open If we don't have valid key and location set, then always just say closed
     *
     * @return
     */
    public boolean isOpen() {
        if (squareEnabled && getLocation() != null) {
            return new BusinessHours(loc.getBusinessHours().getPeriods()).isOpen();
        }
        return false;
    }

    private class BusinessHours extends ArrayList<OpenPeriod> {

        public BusinessHours(List<BusinessHoursPeriod> periods) {
            periods.forEach(p -> add(new OpenPeriod(p)));
        }

        public boolean isOpen() {
            // The current time in the TZ
            final var now = ZonedDateTime.now(tz);
            final var today = now.toLocalDate();

            return stream()
                    .filter(p -> p.getDow().equals(now.getDayOfWeek()))
                    .anyMatch(p -> {
                        final var start = LocalDateTime.of(today, p.getStart()).atZone(tz);
                        final var end = LocalDateTime.of(today, p.getEnd()).atZone(tz);
                        return now.isAfter(start) && now.isBefore(end);
                    });
        }
    }

    @Data
    private static class OpenPeriod {

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
