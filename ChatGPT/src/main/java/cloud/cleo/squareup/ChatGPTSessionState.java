/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup;

import static cloud.cleo.squareup.ChatGPTLambda.HANGUP_FUNCTION_NAME;
import static cloud.cleo.squareup.ChatGPTLambda.TRANSFER_FUNCTION_NAME;
import cloud.cleo.squareup.functions.AbstractFunction;
import com.theokanning.openai.completion.chat.ChatMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedList;
import java.util.List;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Object to track use of ChatGPT
 *
 * @author sjensen
 */
@DynamoDbBean
@Data
public class ChatGPTSessionState {

    private String phoneNumber;
    private LocalDate date;
    private List<ChatGPTMessage> messages;
    private Long counter;
    private Instant lastUpdate;
    private Long ttl;

    public ChatGPTSessionState() {
        this.messages = new LinkedList<>();
    }

    public ChatGPTSessionState(String phoneNumber, LexInputMode inputMode) {
        this.phoneNumber = phoneNumber;
        this.date = LocalDate.now(ZoneId.of("America/Chicago"));
        this.messages = new LinkedList<>();

        final var sb = new StringBuilder();

        // General Prompting
        sb.append("Please be a helpfull assistant for a retail store named \"Copper Fox Gifts\", which has clothing items, home decor, gifts of all kinds, speciality foods, and much more.  ");
        sb.append("The store is located at 160 Main Street, Wahkon Minnesota, near Lake Mille Lacs.  ");
        sb.append("The current date is  ").append(date).append(".  ");
        sb.append("Do not respond with the whole employee list.  You may confirm the existance of an employee and give the full name.  ");

        // Local Stuff to recommend
        sb.append("Muggs of Mille Lacs is a great restaurant next door that serves some on the best burgers in the lake area and has a large selection draft beers and great pub fare.  ");
        sb.append("Tulibee Tavern is another great restaurant across the street that serves more home cooked type meals at reasonable prices.  ");

        // Mode specific prompting
        switch (inputMode) {
            case TEXT -> {
                sb.append("I am interacting via SMS.  Please keep answers very short and concise, preferably under 180 characters.  Do not use markdown in responses.  ");
                sb.append("To interact with an employee suggest the person call ").append(System.getenv("MAIN_NUMBER")).append(" and ask to speak to that person.  ");
                sb.append("Do not provide employee phone numbers.");
            }
            case SPEECH, DTMF -> {
                sb.append("I am interacting with speech via a telephone interface.  please keep answers short and concise.  ");

                // Hangup
                sb.append("When the caller indicates they are done with the conversation, execute the ").append(HANGUP_FUNCTION_NAME).append(" function.  ");

                // Transferring
                if (AbstractFunction.squareEnabled) {
                    sb.append("To transfer or speak with a employee that has a phone number, execute the ").append(TRANSFER_FUNCTION_NAME).append(" function.  ");
                    sb.append("Do not provide callers employee phone numbers, you can use the phone numbers to execute the ").append(TRANSFER_FUNCTION_NAME).append(" function.  ");
                }
                sb.append("If the caller wants to just speak to a person or leave a voicemail, execute ").append(TRANSFER_FUNCTION_NAME).append(" with ").append(System.getenv("MAIN_NUMBER")).append(" which rings the main phone in the store.  ");

            }
        }

        this.messages.add(new ChatGPTMessage(ChatGPTMessage.MessageRole.system, sb.toString()));

        // Expire entries after 30 days
        this.ttl = Instant.now().plus(Duration.ofDays(30)).getEpochSecond();
        this.counter = 0L;
    }

    /**
     * @return the phoneNumber
     */
    @DynamoDbPartitionKey
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * @return the date
     */
    @DynamoDbSortKey
    public LocalDate getDate() {
        return date;
    }

    public void addUserMessage(String message) {
        messages.add(new ChatGPTMessage(ChatGPTMessage.MessageRole.user, message));
    }

    public void addSystemMessage(String message) {
        messages.add(new ChatGPTMessage(ChatGPTMessage.MessageRole.system, message));
    }

    public void addAssistantMessage(String message) {
        messages.add(new ChatGPTMessage(ChatGPTMessage.MessageRole.assistant, message));
    }

    public void addMessage(ChatMessage cm) {
        messages.add(new ChatGPTMessage(cm));
    }

    @DynamoDbIgnore
    public List<ChatMessage> getChatMessages() {
        final var cms = new LinkedList<ChatMessage>();
        messages.forEach(m -> cms.add(m));
        return cms;
    }

    public void incrementCounter() {
        counter = counter + 1L;
    }

}
