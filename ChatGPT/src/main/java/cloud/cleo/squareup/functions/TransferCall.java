/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.ChatGPTLambda.TRANSFER_FUNCTION_NAME;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.function.Function;

/**
 *  Transfer the caller.
 * 
 * @author sjensen
 * @param <Request>
 */
public class TransferCall<Request> extends AbstractFunction {


    @Override
    public String getName() {
        return TRANSFER_FUNCTION_NAME;
    }

    @Override
    public String getDescription() {
        return "Transfer the caller to employee or the main store number only.  Cannot be used to transfer to arbritary numbers";
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
            return mapper.createObjectNode().put("message", "The caller is now ready to be transferred.");
        };
    }

    private static class Request {
        @JsonPropertyDescription("the phone number in E164 format to transfer the caller to")
        @JsonProperty(value = "transfer_number",required = true)
        public String transfer_number;
    }

   /**
     * Transfer not applicable when using a text interface
     * @return 
     */
    @Override
    protected boolean isText() {
        return false;
    }
    
    /**
     * Call leaves GPT and back to Chime.  Voice Only.
     * @return 
     */
    @Override
    public boolean isTerminating() {
        return true;
    }

}
