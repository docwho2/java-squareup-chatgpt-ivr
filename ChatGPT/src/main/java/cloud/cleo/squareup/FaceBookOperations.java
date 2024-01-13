package cloud.cleo.squareup;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.NonNull;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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

    private final static ObjectMapper mapper = new ObjectMapper();

    private final static OkHttpClient httpClient = new OkHttpClient();

    /**
     * Transfer control of Messenger Thread Session from Bot control to the Inbox. Used when end user needs to deal with
     * a real person to resolve issue the Bot can't handle. Some people despise Bots, so we need to allow getting the
     * Bot out of the conversation.
     *
     * https://developers.facebook.com/docs/messenger-platform/handover-protocol/conversation-control
     *
     * @param id
     */
    public static void transferToInbox(String id) {
        try {
            // Construct the payload
            final var json = mapper.createObjectNode();
            // Special Target for Inbox
            json.put("target_app_id", "263902037430900");
            // The page scoped user ID of the person chatting with us
            json.putObject("recipient").put("id", id);

            // Create the request body
            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

            // Build the POST request
            Request request = new Request.Builder()
                    .url(getFaceBookURL(System.getenv("FB_PAGE_ID"), "pass_thread_control"))
                    .post(body)
                    .addHeader("Accept", "application/json")
                    .build();

            // Execute the request and retrieve the response
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Unexpected code " + response);
                }

                // Parse the response as needed
                final var result = mapper.readTree(response.message().getBytes());
                log.debug("FB Pass Thread Control result is " + result.toPrettyString());

                if (result.findValue("success") != null && result.findValue("success").asBoolean()) {
                    log.debug("Call Succeeded in passing thread control");
                } else {
                    log.debug("Call FAILED to pass thread control");
                }
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
            // Build the GET request
            Request request = new Request.Builder()
                    .url(getFaceBookURL(id, null))
                    .addHeader("Accept", "application/json")
                    .build();

            // Execute the request and get the response
            try (Response response = httpClient.newCall(request).execute()) {
                // Check if the response is successful
                if (!response.isSuccessful()) {
                    throw new Exception("Unexpected code " + response);
                }

                // Parse the JSON response
                final var result = mapper.readTree(response.message().getBytes());
                log.debug("FB Graph Query result is " + result.toPrettyString());

                // Check for name first
                if (result.findValue("name") != null) {
                    return result.findValue("name").asText();
                }

                // Usually returns first and last
                if (result.findValue("first_name") != null && result.findValue("last_name") != null) {
                    return result.findValue("first_name").asText() + " " + result.findValue("last_name").asText();
                }
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
        final var sb = new StringBuilder("https://graph.facebook.com/");

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
