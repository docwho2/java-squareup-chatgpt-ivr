package cloud.cleo.squareup;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Perform various Facebook operations. Used when Channel is FB.
 *
 * @author sjensen
 */
public class FaceBookOperations {

    // Initialize the Log4j logger.
    private static final Logger log = LogManager.getLogger(FaceBookOperations.class);

    private final static ObjectMapper mapper = new ObjectMapper();

    
    /**
     * Transfer control of Messenger Thread Session from Bot control to the Inbox.  Used when end user needs to deal with
     * a real person to resolve issue the Bot can't handle.
     * https://developers.facebook.com/docs/messenger-platform/handover-protocol/conversation-control
     * 
     * @param id 
     */
    public static void transferToInbox(String id) {
        try {
            HttpURLConnection connection = (HttpURLConnection) getFaceBookURL(System.getenv("FB_PAGE_ID"), "pass_thread_control").openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // Construct the payload
            var json = mapper.createObjectNode();
            // Special Target for Inbox
            json.put("target_app_id", "263902037430900");
            json.putObject("recipient").put("id", id);

            log.debug("Post Payload for thread control " + json.toPrettyString());
            mapper.writeValue(connection.getOutputStream(), json);

            int responseCode = connection.getResponseCode();
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
        }
    }

    /**
     * Given a Facebook user Page Scoped ID get the users full name
     *
     * @param id
     * @return
     */
    public static String getFacebookName(String id) {
        try {
            HttpURLConnection connection = (HttpURLConnection) getFaceBookURL(id, null).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
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
    private static URL getFaceBookURL(@NonNull String id, String operation) throws MalformedURLException {
        StringBuilder sb = new StringBuilder("https://graph.facebook.com/");

        // Version of API we are calling
        sb.append("v18.0/");

        // ID for the entity we are using (Page ID, or Page scoped User ID)
        sb.append(id);

        // Optional operation
        if (operation != null) {
            sb.append('/').append(operation);
        }

        sb.append("?access_token=").append(System.getenv("FB_PAGE_ACCESS_TOKEN"));

        return new URL(sb.toString());
    }
}
