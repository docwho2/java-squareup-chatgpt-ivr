/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.ChatGPTLambda.crtAsyncHttpClient;
import static cloud.cleo.squareup.functions.AbstractFunction.log;
import static cloud.cleo.squareup.functions.AbstractFunction.mapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletionException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;

/**
 *
 * @author sjensen
 */
public interface SendSMS {

    public final static SnsAsyncClient snsAsyncClient = SnsAsyncClient.builder()
            // Force SMS sending to east because that's where all the 10DLC and campaign crap setup is done
            // Otherwise have to pay for registrations and numbers in 2 regions, HUGE HASSLE (and more monthly cost)
            // Also then all texts are sourced from the same phone number for consistancy
            .region(Region.US_EAST_1)
            .httpClient(crtAsyncHttpClient)
            .build();

    /**
     * Used to send SMS to the caller's cell phone.
     * 
     * @param phoneNumber to send the message to
     * @param message body to send
     * @return 
     */
    public static JsonNode sendSMS(String phoneNumber, String message) {
        try {
            final var result = snsAsyncClient.publish(b -> b.phoneNumber(phoneNumber).message(message)).join();
            log.info("SMS [" + message +  "] sent to " + phoneNumber + " with SNS id of " + result.messageId());
            return mapper.createObjectNode().put("status", "SUCCESS").put("message", "The SMS message was successfuly sent to the caller");
        } catch (CompletionException e) {
            log.error("Could not send message via SMS to caller", e.getCause());
            return mapper.createObjectNode().put("status", "FAILED").put("message", "An error has occurred, this function may be down");
        } catch (Exception e) {
            log.error("Could not send message via SMS to caller", e);
            return mapper.createObjectNode().put("status", "FAILED").put("message", "An error has occurred, this function may be down");
        }
    }
}
