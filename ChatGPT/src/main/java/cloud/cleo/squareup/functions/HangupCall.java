package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.ChatGPTLambda.HANGUP_FUNCTION_NAME;
import java.util.function.Function;

/**
 *  Hangup the caller.
 * 
 * @author sjensen
 * @param <Request>
 */
public class HangupCall<Request> extends AbstractFunction {


    @Override
    public String getName() {
        return HANGUP_FUNCTION_NAME;
    }

    @Override
    public String getDescription() {
        return "Should be called when the interaction is done and the caller no longer needs any further assistance";
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
            return mapper.createObjectNode().put("message", "The caller is now ready to hangup. session ended.");
        };
    }

    private static class Request {
    }

   /**
     * Hangup not applicable when using a text interface
     * @return 
     */
    @Override
    protected boolean isText() {
        return false;
    }

}
