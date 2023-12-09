package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.enums.ChannelPlatform.FACEBOOK;
import static cloud.cleo.squareup.functions.AbstractFunction.log;
import static cloud.cleo.squareup.functions.AbstractFunction.mapper;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Function;

/**
 * End Bot session and pass control of messaging thread to Inbox.
 *
 *
 * @author sjensen
 * @param <Request>
 */
public class FacebookInbox<Request> extends AbstractFunction {

    @Override
    public String getName() {
        return "facebook_inbox";
    }

    @Override
    public String getDescription() {
        return "Should be called when the interaction requires a person to take over the conversation";
    }

    @Override
    public Class getRequestClass() {
        return Request.class;
    }

    /**
     * https://developers.facebook.com/docs/messenger-platform/handover-protocol/conversation-control
     * @return
     */
    @Override
    public Function<Request, Object> getExecutor() {
        return (var r) -> {
            try {
                // TODO replace hard coded page ID
                URL url = new URL("https://graph.facebook.com/v18.0/105958158478591/pass_thread_control?access_token=" 
                        + System.getenv("FB_PAGE_ACCESS_TOKEN")
                        + "&recipient=" + getSessionId()
                        // The below app ID is a special ID for Inbox
                        + "&target_app_id=263902037430900"
                );
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");

                int responseCode = connection.getResponseCode();
                log.debug("Facebook Call Response Code: " + responseCode);

                final var result = mapper.readTree(connection.getInputStream());
                log.debug("FB Pass Thread Control result is " + result.toPrettyString());

                if ( result.findValue("success") != null && result.findValue("success").asBoolean() == true) {
                   log.debug("Call Succeeded in passing thread control");
                } else {
                    log.debug("Call FAILED to pass thread control");
                }

            } catch (Exception e) {
                log.error("Facebook user name retrieval error", e);
            }
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
