/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup;

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

    public ChatGPTSessionState(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.date = LocalDate.now(ZoneId.of("America/Chicago"));
        this.messages = new LinkedList<>();
        var initMsg = new ChatGPTMessage(ChatGPTMessage.MessageRole.system, """
        I am interacting via a telephone interface.  please keep answers short and concise.
        Please be a helpfull assistant for a retail store named "Copper Fox Gifts".
        The store is located at 160 Main Street, Wahkon MN  56386 near lake Mille Lacs.
        Muggs of Mille Lacs is a great resturant next door that serves some on the best burgers in the lake area and has a large selection draft beers and great pub fare.                                                                                                                
        When the caller indicates they are done with the conversation, please respond with just the word "HANGUP".
        To transfer or speak with a Team Member that has a phone number, please respond with just the word "TRANSFER" followed by the E164 phone number.                                                                    
        """);

        //initMsg.setName("TelephoneTimesheets");
        this.messages.add(initMsg);

        // Expire entries after 60 days
        this.ttl = Instant.now().plus(Duration.ofDays(60)).getEpochSecond();
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
