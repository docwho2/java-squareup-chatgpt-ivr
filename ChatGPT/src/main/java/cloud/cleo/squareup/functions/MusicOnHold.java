package cloud.cleo.squareup.functions;

import java.util.function.Function;

/**
 *  Play music to the caller and let them hit a key to return to GPT
 * 
 * @author sjensen
 * @param <Request>
 */
public class MusicOnHold<Request> extends AbstractFunction {


    @Override
    public String getName() {
        return "hold_call";
    }

    @Override
    public String getDescription() {
        return "Should be called when the caller needs more time to respond or requests more time so they can be put on hold";
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
            return mapper.createObjectNode().put("message", "The caller will now be placed on hold and hear music");
        };
    }

    private static class Request {
    }

   /**
     * MOH not applicable when using a text interface
     * @return 
     */
    @Override
    protected boolean isText() {
        return false;
    }
    
    /**
     * If we don't have a Voice Connector, then we can't transfer to the SIP MOH server.
     * music@iptel.org.  Therefore disable this function.
     * @return 
     */
    @Override
    protected boolean isEnabled() {
        final var vc_arn = System.getenv("VC_ARN");
        return vc_arn != null && ! vc_arn.isBlank() && ! vc_arn.equalsIgnoreCase("PSTN");
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
