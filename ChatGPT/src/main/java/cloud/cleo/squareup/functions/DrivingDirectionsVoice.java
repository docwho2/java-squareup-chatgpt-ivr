package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.ChatGPTLambda.crtAsyncHttpClient;
import java.util.function.Function;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;

/**
 * Driving Directions when user is interacting via Voice interface.
 *
 * @author sjensen
 */
public class DrivingDirectionsVoice extends AbstractDrivingDirections {

    
    final static SnsAsyncClient snsAsyncClient = SnsAsyncClient.builder()
                        // Force SMS sending to east because that's where all the 10DLC and campaign crap setup is done
                        // Otherwise have to pay for registrations and numbers in 2 regions, HUGE HASSLE (and more monthly cost)
                        // Also then all texts are sourced from the same phone number for consistancy
                        .region(Region.US_EAST_1)
                        .httpClient(crtAsyncHttpClient)
                        .build();
    
    @Override
    protected String getDescription() {
        return "Sends the caller a URL with directions to the store direct to their mobile device. Confirm they are calling from a mobile device before calling this function.";
    }

    @Override
    protected Function getExecutor() {
        return (var r) -> {
            try (snsAsyncClient) {
                final var callingNumber = getCallingNumber();
                
                // Only send SMS to validated US Phone Numbers (in case callerID block, or some weird deal)
                if ( ! hasValidUSE164Number() ) {
                    return mapper.createObjectNode().put("status","FAILED").put("message", "Calling number is not a valid US phone number");
                }
                
                // Do not attempt to send to non-mobile numbers
                if ( ! hasValidUSMobileNumber() ) {
                    return mapper.createObjectNode().put("status","FAILED").put("message", "Caller is not calling from a mobile device");
                }
                
                final var result = snsAsyncClient.publish(b -> b.phoneNumber(callingNumber).message(DRIVING_DIRECTIONS_URL) ).join();
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
