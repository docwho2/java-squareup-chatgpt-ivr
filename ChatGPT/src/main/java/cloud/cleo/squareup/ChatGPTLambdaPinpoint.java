package cloud.cleo.squareup;

import static cloud.cleo.squareup.lang.LangUtil.LanguageIds.UNHANDLED_EXCEPTION;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.LexV2Response;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.concurrent.CompletionException;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;

/**
 * Process incoming SMS from Pinpoint and respond as another Channel Type.
 *
 * https://docs.aws.amazon.com/sms-voice/latest/userguide/phone-numbers-two-way-sms.html
 *
 * @author sjensen
 */
public class ChatGPTLambdaPinpoint extends ChatGPTLambda implements RequestHandler<SNSEvent, Void> {

    // Initialize the Log4j logger.
    Logger log = LogManager.getLogger(ChatGPTLambdaPinpoint.class);

    final static SnsAsyncClient snsAsyncClient = SnsAsyncClient.builder()
            // Force SMS sending to east because that's where all the 10DLC and campaign crap setup is done
            // Otherwise have to pay for registrations and numbers in 2 regions, HUGE HASSLE (and more monthly cost)
            // Also then all texts are sourced from the same phone number for consistancy
            .region(Region.US_EAST_1)
            .httpClient(crtAsyncHttpClient)
            .build();

    @Override
    public Void handleRequest(SNSEvent input, Context cntxt) {
        // Only 1 record is every presented
        SNSEvent.SNS snsEvent = input.getRecords().get(0).getSns();
        log.debug("Recieved SNS Event" + snsEvent);

        // Convert payload to Pinpoint Event
        final var ppe = mapper.convertValue(snsEvent, PinpointEvent.class);

        // Wrapped Event Class
        final LexV2EventWrapper event = new LexV2EventWrapper(ppe);
        LexV2Response response;
        try {
            response = processGPT(event);
        } catch (CompletionException e) {
            log.error("Unhandled Future Exception", e.getCause());
            response = buildResponse(event, event.getLangString(UNHANDLED_EXCEPTION));
        } catch (Exception e) {
            log.error("Unhandled Exception", e);
            // Unhandled Exception
            response = buildResponse(event, event.getLangString(UNHANDLED_EXCEPTION));
        }

        // Take repsonse body message from the LexV2Reponse and respond to SMS via SNS
        final var botResponse = response.getMessages()[0].getContent();
        final var result = snsAsyncClient.publish(b -> b.phoneNumber(ppe.getOriginationNumber())
                .message(botResponse))
                .join();
        log.info("SMS Bot Response sent to " + ppe.getOriginationNumber() + " with SNS id of " + result.messageId());

        return null;
    }

    /**
     * The SNS payload we will receive from Incoming SMS messages.
     *
     */
    @Data
    public static class PinpointEvent {

        /**
         * The phone number that sent the incoming message to you (in other words, your customer's phone number).
         */
        @JsonProperty(value = "originationNumber")
        private String originationNumber;

        /**
         * The phone number that the customer sent the message to (your dedicated phone number).
         */
        @JsonProperty(value = "destinationNumber")
        private String destinationNumber;

        /**
         * The registered keyword that's associated with your dedicated phone number.
         */
        @JsonProperty(value = "messageKeyword")
        private String messageKeyword;

        /**
         * The message that the customer sent to you.
         */
        @JsonProperty(value = "messageBody")
        private String messageBody;

        /**
         * The unique identifier for the incoming message.
         */
        @JsonProperty(value = "inboundMessageId")
        private String inboundMessageId;

        /**
         * The unique identifier of the message that the customer is responding to.
         */
        @JsonProperty(value = "previousPublishedMessageId")
        private String previousPublishedMessageId;
    }

}
