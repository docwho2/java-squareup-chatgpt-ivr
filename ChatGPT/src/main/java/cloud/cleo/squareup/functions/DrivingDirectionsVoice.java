package cloud.cleo.squareup.functions;

import java.util.function.Function;

/**
 * Driving Directions when user is interacting via Voice interface.
 *
 * @author sjensen
 */
public class DrivingDirectionsVoice extends DrivingDirections implements SendSMS {

    
    @Override
    protected String getDescription() {
        return "Sends the caller a URL with driving directions to the store via SMS.";
    }

    @Override
    protected Function getExecutor() {
        return (var r) -> {

                final var callingNumber = getCallingNumber();
                
                // Only send SMS to validated US Phone Numbers (in case callerID block, or some weird deal)
                if ( ! hasValidUSE164Number() ) {
                    return mapper.createObjectNode().put("status","FAILED").put("message", "Calling number is not a valid US phone number");
                }
                
                // Do not attempt to send to non-mobile numbers
                if ( ! hasValidUSMobileNumber() ) {
                    return mapper.createObjectNode().put("status","FAILED").put("message", "Caller is not calling from a mobile device");
                }
                
                return SendSMS.sendSMS(callingNumber,DRIVING_DIRECTIONS_URL);
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
