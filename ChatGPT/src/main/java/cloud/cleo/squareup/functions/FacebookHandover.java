package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.enums.ChannelPlatform.FACEBOOK;
import static cloud.cleo.squareup.functions.AbstractFunction.mapper;
import java.util.function.Function;
import static cloud.cleo.squareup.ChatGPTLambda.FACEBOOK_HANDOVER_FUNCTION_NAME;

/**
 * End Bot session and pass control of messaging thread to Inbox.
 *
 *
 * @author sjensen
 * @param <Request>
 */
public class FacebookHandover<Request> extends AbstractFunction {

    @Override
    public String getName() {
        return FACEBOOK_HANDOVER_FUNCTION_NAME;
    }

    @Override
    public String getDescription() {
        return "Should be called when the interaction requires a person to take over the conversation.";
    }

    @Override
    public Class getRequestClass() {
        return Request.class;
    }

    /**
     * https://developers.facebook.com/docs/messenger-platform/handover-protocol/conversation-control
     * When this function is called we will control the response and perform the actual FB API call later.
     * 
     * @return
     */
    @Override
    public Function<Request, Object> getExecutor() {
        return (var r) -> {
            return mapper.createObjectNode().put("message", "Conversation has been moved to the Inbox, a person will respond shortly.");
        };
    }

    private static class Request {
    }

    /**
     * This should only be enabled when the channel is Facebook (which is text)
     *
     * @return
     */
    @Override
    protected boolean isText() {
        return getChannelPlatform().equals(FACEBOOK);
    }

    /**
     * Not applicable to Voice since only FB.
     *
     * @return
     */
    @Override
    protected boolean isVoice() {
        return false;
    }

}
