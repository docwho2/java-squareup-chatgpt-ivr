package cloud.cleo.squareup.functions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.squareup.square.types.BusinessHoursPeriod;
import com.squareup.square.types.GetLocationsRequest;
import com.squareup.square.types.Location;
import java.time.DayOfWeek;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * Return the store hours from Square API
 *
 * @author sjensen
 * @param <Request>
 */
public class SquareHours<Request> extends AbstractFunction {

    private static volatile Location cachedLocation; // Cache for the last successful location data

    @Override
    public String getName() {
        return "store_hours";
    }

    @Override
    public String getDescription() {
        return "Return the open store hours by day of week along with current open/closed status and current date/time.  Any day of week not returned means the store is closed that day.";
    }

    @Override
    public Class getRequestClass() {
        return Request.class;
    }

    /**
     *
     * @return
     */
    @Override
    public Function<Request, Object> getExecutor() {
        return (var r) -> {
            try {
                final Location loc = getLocation();
                final var bh = new BusinessHours(loc);

                final var tz = ZoneId.of(loc.getTimezone().get());
                final var now = ZonedDateTime.now(tz);
                final var dow = now.getDayOfWeek();

                /**
                 * GPT gives wrong information sometimes saying its open when store is closed. Giving it the concrete
                 * status of OPEN or CLOSED seems to help with a timestamp. Sometimes even though it knows the date, it
                 * says the wrong day of week too, so added that as well returning all this info vs just the periods
                 * seems to fix everything and I can't get it to return wrong answer anymore
                 */
                final ObjectNode json = mapper.createObjectNode();
                json.put("open_closed_status", bh.isOpen() ? "OPEN" : "CLOSED");
                json.put("current_date_time", now.toString());
                json.put("current_day_of_week", now.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US).toUpperCase());
                json.putPOJO("open_hours",  bh);

                return json;
            } catch (Exception ex) {
                log.error("Unhandled Error", ex);
                return mapper.createObjectNode().put("error_message", ex.getLocalizedMessage());
            }
        };
    }

    private static class Request {
    }

    /**
     * Gets the location data, using cache if the Square API call fails.
     *
     * @return the Location object
     * @throws Exception if an error occurs and no cached data is available
     */
    private Location getLocation() throws Exception {
        try {
            Location loc = getSquareClient().locations().get( GetLocationsRequest.builder().locationId(System.getenv("SQUARE_LOCATION_ID")).build()).get().getLocation().get();
            cachedLocation = loc;
            return loc;
        } catch (Exception ex) {
            log.error("Failed to retrieve location from Square API, using cached data if available", ex);
            if (cachedLocation != null) {
                return cachedLocation;
            } else {
                throw new Exception("No cached data available and failed to retrieve from Square API", ex);
            }
        }
    }


    private static class BusinessHours extends ArrayList<OpenPeriod> {

        @JsonIgnore
        private final Location loc;

        public BusinessHours(Location loc) {
            this.loc = loc;
            loc.getBusinessHours().get().getPeriods().get().forEach(p -> add(new OpenPeriod(p)));
        }

        @JsonIgnore
        public boolean isOpen() {
            // The current time in the TZ
            final var tz = ZoneId.of(loc.getTimezone().get());
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

    @Getter(AccessLevel.PUBLIC)
    private static class OpenPeriod {

        final DayOfWeek dow;
        final LocalTime start;
        final LocalTime end;

        public OpenPeriod(BusinessHoursPeriod bhp) {
            dow = switch (bhp.getDayOfWeek().get().getEnumValue()) {
                case SUN ->
                    SUNDAY;
                case MON ->
                    MONDAY;
                case TUE ->
                    TUESDAY;
                case WED ->
                    WEDNESDAY;
                case THU ->
                    THURSDAY;
                case FRI ->
                    FRIDAY;
                case SAT ->
                    SATURDAY;
                case UNKNOWN ->
                    throw new RuntimeException("Day of Week Cannot be matched " + bhp.getDayOfWeek());
            };

            start = LocalTime.parse(bhp.getStartLocalTime().get());
            end = LocalTime.parse(bhp.getEndLocalTime().get());
        }
    }

    @Override
    protected boolean isEnabled() {
        return isSquareEnabled();
    }
}
