package cloud.cleo.squareup.functions;

import java.math.BigDecimal;
import java.util.function.Function;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Driving Directions when user is interacting via Voice interface.
 *
 * @author sjensen
 */
public class DrivingDirectionsVoice extends AbstractDrivingDirections {

    @Override
    protected String getDescription() {
        return "Sends the caller a URL with directions to the store direct to their mobile device. Confirm they are calling from a mobile device before calling this function.";
    }

    @Override
    protected Function getExecutor() {
        return (var r) -> {
            try {
                final var callingNumber = getCallingNumber();
                
                // Only send SMS to validated US Phone Numbers
                if ( ! isValidUSE164Number(callingNumber) ) {
                    return mapper.createObjectNode().put("status","FAILED").put("message", "Calling number is not a valid US phone number");
                }
                
                final var result = SnsClient.create().publish(b -> b.phoneNumber(callingNumber).message(DRIVING_DIRECTIONS_URL).build());
                log.info("SMS Directions sent to " + callingNumber + " with SNS id of " + result.messageId());
                return mapper.createObjectNode().put("status","SUCCESS").put("message", "The directions have been sent");
            } catch (Exception e) {
                log.error("Could not send Directions via SMS to caller",e);
                return mapper.createObjectNode().put("status","FAILED").put("message", "An error has occurred, this function may be down");
            }
        };
    }
    
    


    /**
     * This function is Voice only so don't use this for text interface
     *
     * @return
     */
    @Override
    protected boolean isText() {
        return false;
    }

}
