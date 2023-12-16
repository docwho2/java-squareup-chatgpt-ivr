package cloud.cleo.squareup.functions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Function;

/**
 * Schedule a Private Shopping Event (just testing for now)
 *
 * @author sjensen
 * @param <Request>
 */
public class SchedulePrivateShopping<Request> extends AbstractFunction {

    @Override
    public String getName() {
        return "schedule_private_shoping";
    }

    @Override
    public String getDescription() {
        return "Schedule a private Shopping session for 4 of more people at the store, guide user asking for each parameter";
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
                // TODO . call Sqauare API to schedule
                return mapper.createObjectNode().put("status", "SUCCESS").put("message", "The event has been scheduled.");
            } catch (Exception e) {
                log.error("Unhandled Error", e);
                return mapper.createObjectNode().put("status", "FAILED").put("message", "An error has occurred, the event could not be scheduled.");
            }
        };
    }

    private static class Request {

        @JsonPropertyDescription("Name of the customer who would like to schedule the shopping event, please ask them for their name.")
        @JsonProperty(required = true)
        public String customer_name;
        
        @JsonPropertyDescription("The date in ISO format for the private shopping event")
        @JsonProperty(required = true)
        public LocalDate date;

        @JsonPropertyDescription("The time in ISO format for the private shopping event")
        @JsonProperty(required = true)
        public LocalTime time;

//        @JsonPropertyDescription("Optional name of the employee requested to provide the event, must be a valid employee first name")
//        @JsonProperty(required = false)
//        public String employee_first_name;
    }

    /**
     * Square must be enabled or their won't be a way to schedule.
     *
     * @return
     */
    @Override
    protected boolean isEnabled() {
        //return isSquareEnabled();
        return false; // Disable for now until square call is coded up
    }

}
