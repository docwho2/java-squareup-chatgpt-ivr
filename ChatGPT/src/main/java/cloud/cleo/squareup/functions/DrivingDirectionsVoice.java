package cloud.cleo.squareup.functions;

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
                final var result = SnsClient.create().publish(b -> b.phoneNumber(getCallingNumber()).message(DRIVING_DIRECTIONS_URL).build());
                log.info("SMS Directions sent to " + getCallingNumber() + " with SNS id of " + result.messageId());
                return mapper.createObjectNode().put("status", "The directions have been sent");
            } catch (Exception e) {
                log.error("Could not send Directions via SMS to caller",e);
                return mapper.createObjectNode().put("status", "An error has occurred, this function may be down");
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
