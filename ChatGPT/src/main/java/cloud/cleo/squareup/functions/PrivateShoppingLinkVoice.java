/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.ChatGPTLambda.PRIVATE_SHOPPING_VOICE_FUNCTION_NAME;
import java.util.function.Function;

/**
 *
 * @author sjensen
 */
public class PrivateShoppingLinkVoice extends PrivateShoppingLink implements SendSMS {

    @Override
    public final String getName() {
        return PRIVATE_SHOPPING_VOICE_FUNCTION_NAME;
    }
    
    @Override
    protected String getDescription() {
        return "Sends the caller the direct URL to book private shopping";
    }

    @Override
    protected Function getExecutor() {
        return (var r) -> {
            final var callingNumber = getCallingNumber();

            // Only send SMS to validated US Phone Numbers (in case callerID block, or some weird deal)
            if (!hasValidUSE164Number()) {
                return mapper.createObjectNode().put("status", "FAILED").put("message", "Calling number is not a valid US phone number");
            }

            // Do not attempt to send to non-mobile numbers
            if (!hasValidUSMobileNumber()) {
                return mapper.createObjectNode().put("status", "FAILED").put("message", "Caller is not calling from a mobile device");
            }

            return SendSMS.sendSMS(callingNumber, PRIVATE_SHOPPING_URL);
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
    
     @Override
    protected boolean isVoice() {
        return true;
    }
}
