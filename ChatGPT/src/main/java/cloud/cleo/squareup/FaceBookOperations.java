package cloud.cleo.squareup;

import static cloud.cleo.squareup.ChatGPTLambda.mapper;
import static cloud.cleo.squareup.functions.PrivateShoppingLink.PRIVATE_SHOPPING_URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Perform various Facebook operations. Used when Channel is FB. Rather than pull in some other dependency, we will just
 * use basic HTTP for all Facebook operations.
 *
 * @author sjensen
 */
public class FaceBookOperations {

    // Initialize the Log4j logger.
    private static final Logger log = LogManager.getLogger(FaceBookOperations.class);

    /**
     * Transfer control of Messenger Thread Session from Bot control to the Inbox. Used when end user needs to deal with
     * a real person to resolve issue the Bot can't handle. Some people despise Bots, so we need to allow getting the
     * Bot out of the conversation.
     *
     * https://developers.facebook.com/docs/messenger-platform/handover-protocol/conversation-control
     *
     * @param id of the recipient
     */
    public static void transferToInbox(String id) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) getFaceBookURL(System.getenv("FB_PAGE_ID"), "pass_thread_control").openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // Construct the payload
            var json = mapper.createObjectNode();
            // Special Target for Inbox
            json.put("target_app_id", "263902037430900");
            // The page scoped user ID of the person chatting with us
            json.putObject("recipient").put("id", id);

            log.debug("Post Payload for thread control " + json.toPrettyString());
            mapper.writeValue(connection.getOutputStream(), json);

            final int responseCode = connection.getResponseCode();
            log.debug("Facebook Call Response Code: " + responseCode);

            final var result = mapper.readTree(connection.getInputStream());
            log.debug("FB Pass Thread Control result is " + result.toPrettyString());

            if (result.findValue("success") != null && result.findValue("success").asBoolean() == true) {
                log.debug("Call Succeeded in passing thread control");
            } else {
                log.debug("Call FAILED to pass thread control");
            }

        } catch (Exception e) {
            log.error("Facebook Pass Thread Control error", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    
    /**
     * Adds a static menu button, so when bot calls for the URL, will persist as menu item.
     * https://developers.facebook.com/docs/messenger-platform/send-messages/persistent-menu/
     * @param id
     * @return 
     */
    public static void addPrivateShoppingMenu(String id) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) getFaceBookURL(null, "me/custom_user_settings").openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // Construct the payload
            var json = mapper.createObjectNode();

            // The page scoped user ID of the person chatting with us
            json.put("psid", id);

            json.putArray("persistent_menu")
                    .addObject()
                    .put("locale", "default")
                    .put("composer_input_disabled", false)
                    .putArray("call_to_actions")
                    .addObject()
                    .put("type", "web_url")
                    .put("url", "https://" + PRIVATE_SHOPPING_URL)
                    .put("title", "Book Shopping Appointment Now!")
                    .put("webview_height_ratio", "full");

            log.debug("Post Payload for Private Shopping Menu" + json.toPrettyString());
            mapper.writeValue(connection.getOutputStream(), json);

            final int responseCode = connection.getResponseCode();
            log.debug("Facebook Call Response Code: " + responseCode);

            final var result = mapper.readTree(connection.getInputStream());
            log.debug("FB Private Shopping Menu send result is " + result.toPrettyString());

            if (result.findValue("message_id") != null) {
                log.debug("Call Succeeded in sending Private Shopping Menu");
            } else {
                log.debug("Call FAILED to send Private Shopping Menu");
            }

        } catch (Exception e) {
            log.error("Facebook Messenger Private Shopping Menu send failed", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Send our private Shopping URL as a Messenger Button.  Leave here for now,
     * seems cleaner to let Bot send the URL instead of a button.
     *
     * @param id of the recipient
     * @return true if successfully sent
     */
    @Deprecated
    public static boolean sendPrivateBookingURL(String id) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) getFaceBookURL(null, "me/messages").openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // Construct the payload
            var json = mapper.createObjectNode();

            // The page scoped user ID of the person chatting with us
            json.putObject("recipient").put("id", id);

            json.putObject("message").putObject("attachment")
                    .put("type", "template").putObject("payload")
                    .put("template_type", "button")
                    .put("text", "Book Your Private Shopping Experience")
                    .putArray("buttons")
                    .addObject()
                    .put("type", "web_url")
                    //.put("messenger_extensions", true)
                    .put("url", "https://" + PRIVATE_SHOPPING_URL)
                    .put("title", "Book Now!")
                    .put("webview_height_ratio", "full");

            log.debug("Post Payload for URL push" + json.toPrettyString());
            mapper.writeValue(connection.getOutputStream(), json);

            final int responseCode = connection.getResponseCode();
            log.debug("Facebook Call Response Code: " + responseCode);

            final var result = mapper.readTree(connection.getInputStream());
            log.debug("FB Messgene URL send result is " + result.toPrettyString());

            if (result.findValue("message_id") != null) {
                log.debug("Call Succeeded in sending URL in FB Messenger");
                return true;
            } else {
                log.debug("Call FAILED to send URL in FB Messenger");
            }

        } catch (Exception e) {
            log.error("Facebook Messenger send failed", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return false;
    }

    /**
     * Given a Facebook user Page Scoped ID get the users full name
     *
     * @param id
     * @return
     */
    public static String getFacebookName(String id) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) getFaceBookURL(id, null).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            final int responseCode = connection.getResponseCode();
            log.debug("Facebook Call Response Code: " + responseCode);

            final var result = mapper.readTree(connection.getInputStream());
            log.debug("FB Graph Query result is " + result.toPrettyString());

            // Check for name first
            if (result.findValue("name") != null) {
                return result.findValue("name").asText();
            }

            // Usually returns first and last
            if (result.findValue("first_name") != null && result.findValue("last_name") != null) {
                return result.findValue("first_name").asText() + " " + result.findValue("last_name").asText();
            }

        } catch (Exception e) {
            log.error("Facebook user name retrieval error", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return "Unknown";
    }

    /**
     * Get the base URL for Facebook Graph Operations with page access token incorporated.
     *
     * @param id
     * @param operation
     * @return
     * @throws MalformedURLException
     */
    private static URL getFaceBookURL(String id, String operation) throws MalformedURLException {
        final var sb = new StringBuilder("https://graph.facebook.com/");

        // Version of API we are calling
        sb.append("v24.0");

        // ID for the entity we are using (Page ID, or Page scoped User ID)
        if (id != null) {
            sb.append('/').append(id);
        }

        // Optional operation
        if (operation != null) {
            sb.append('/').append(operation);
        }

        sb.append("?access_token=").append(System.getenv("FB_PAGE_ACCESS_TOKEN"));

        return new URL(sb.toString());
    }
}
