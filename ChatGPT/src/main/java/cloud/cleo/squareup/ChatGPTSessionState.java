package cloud.cleo.squareup;

import static cloud.cleo.squareup.ChatGPTLambda.HANGUP_FUNCTION_NAME;
import static cloud.cleo.squareup.ChatGPTLambda.SWITCH_LANGUAGE_FUNCTION_NAME;
import static cloud.cleo.squareup.ChatGPTLambda.TRANSFER_FUNCTION_NAME;
import cloud.cleo.squareup.enums.Language;
import cloud.cleo.squareup.functions.AbstractFunction;
import com.theokanning.openai.completion.chat.ChatMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Object to store and accumulate ChatGPT Session Data (messages) in DynamoDB.
 *
 * @author sjensen
 */
@DynamoDbBean
@Data
public class ChatGPTSessionState {

    /**
     * Session Id which could be phone number or unique identifier depending on the channel.
     */
    private String sessionId;
    /**
     * We qualify all sessions with today's date because channels like Twilio and Facebook have static sessionId's. If
     * we didn't use date as a range key, then static ID's like SMS would have too much session data and accumulate day
     * to day. So essentially SMS and FB sessions will span one day. Chime will generate a unique sessionId per call, so
     * for voice calls, a session is a call.
     */
    private LocalDate date;
    /**
     * ChatGPT messages from the GPT Library.
     */
    private List<ChatGPTMessage> messages;
    /**
     * Counter to track number of interactions, just to see them in Dynamo console to look for longer running chats.
     */
    private Long counter;

    /**
     * Unix timestamp when this Dynamo record should be deleted. We don't want session data hanging in the table
     * forever.
     */
    private Long ttl;

    public ChatGPTSessionState() {
        this.messages = new LinkedList<>();
    }

    public ChatGPTSessionState(LexV2EventWrapper lexRequest) {
        this.sessionId = lexRequest.getSessionId();
        this.date = LocalDate.now(ZoneId.of("America/Chicago"));
        this.messages = new LinkedList<>();

        final var sb = new StringBuilder();

        // General Prompting
        sb.append("Please be a helpfull assistant named \"Copper Bot\" for a retail store named \"Copper Fox Gifts\", which has clothing items, home decor, gifts of all kinds, speciality foods, and much more.  ");
        sb.append("The store is located at 160 Main Street, Wahkon Minnesota, near Lake Mille Lacs.  ");
        sb.append("The store opened in October of 2021 and moved to its larger location in May of 2023.  ");

        // We need to tell GPT the date so it has a reference, when calling via API it has no date knowledge
        // We don't let sessions storage span days, so the date should always be relevant.
        sb.append("The current date is  ").append(date).append(".  ");

        // Local Stuff to recommend
        sb.append("Muggs of Mille Lacs is a great restaurant next door that serves some on the best burgers in the lake area and has a large selection draft beers and great pub fare.  ");
        sb.append("Tulibee Tavern is another great restaurant across the street that serves more home cooked type meals at reasonable prices.  ");

        // We want to receieve all emails in English so we can understand them :-)
        sb.append("When executing send_email_message function, translate the subject and message request parameteres to English.  ");

        // Square must be enabled for all of the below, so exclude when deploying without Sqaure enabled
        if (AbstractFunction.isSquareEnabled()) {
            // Privacy
            sb.append("Do not give out employee phone numbers, only email addresses.  You can give out the main store phone number which is ").append(System.getenv("MAIN_NUMBER")).append(".  ");
            sb.append("Do not give out the employee list.  You may confirm the existance of an employee and give the full name and email.  ");

            // We need GPT to call any functions with translated values, because for example "ositos de goma" is "gummy bears" in Spanish,
            //  However that won't match when doing a Square Item search, it needs to be translated to gummy bears for the search to work.
            // General statement didn't work well, but calling the below out works great
            sb.append("When executing store_product_categories function, translate the search_text to English.  ");
            sb.append("When executing store_product_item function, translate the search_text to English.  ");
            sb.append("Execute store_product_categories for more general search_text and store_product_item for more specific search_text inputs or execute both functions if needed.  ");
            
            // Because we search on all terms, tell GPT to look at results and analyze whether the exact search term matched, or maybe a sub-string matched
            sb.append("When executing store_product_categories or store_product_item function the results may include items that don't match exactly, ")
                    .append("so check to see if the full search_text is contained in the result to indicate an exact match, otherwise indicate to user ")
                    .append("that those results may be similar items to what they asked about.  ");
        }

        // Mode specific prompting
        switch (lexRequest.getInputMode()) {
            case TEXT -> {
                switch (lexRequest.getChannelPlatform()) {
                    case FACEBOOK -> {
                        // Don't need very short or char limit, but we don't want to output a book either
                        sb.append("I am interacting via Facebook Messenger.  Please keep answers short and concise.  ");
                    }
                    case TWILIO -> {
                        // Try and keep SMS segements down, hence the "very" short reference and character preference
                        sb.append("I am interacting via SMS.  Please keep answers very short and concise, preferably under 180 characters.  ");

                        // We can't move conversation to person like Facebook, so tell them to call
                        sb.append("If the user wants to speak or deal with a person in general or leave a voicemail, instruct them to call ")
                                .append(System.getenv("MAIN_NUMBER")).append(" which rings the main phone in the store.  ");
                    }
                    default -> {
                        // Keep very short for anything else (CLI and lex Console testing)
                        sb.append("Please keep answers very short and concise.  ");
                    }
                }
                // Since we are fallback intent, from a Text input perspective, we can support any language ChatGPT understands
                sb.append("Detect the language of the prompt and respond in that language.  ");
            }
            case SPEECH, DTMF -> {
                sb.append("I am interacting with speech via a telephone interface.  please keep answers short and concise.  ");

                // Hangup
                sb.append("When the caller indicates they are done with the conversation, execute the ").append(HANGUP_FUNCTION_NAME).append(" function.  ");

                // Speech Languages and switching between them at any time
                sb.append("If the caller wants to interact in ")
                        .append(Arrays.stream(Language.values()).map(Language::toString).collect(Collectors.joining(" or ")))
                        .append(" execute the ").append(SWITCH_LANGUAGE_FUNCTION_NAME)
                        .append(" function and then respond to all future prompts in that language.  ");

                // Transferring
                if (AbstractFunction.isSquareEnabled()) {
                    sb.append("To transfer or speak with an employee that has a phone number, execute the ").append(TRANSFER_FUNCTION_NAME).append(" function.  ");
                    sb.append("Do not provide callers employee phone numbers, you can only use the phone numbers to execute the ").append(TRANSFER_FUNCTION_NAME).append(" function.  ");
                }
                sb.append("If the caller wants to just speak to a person in general or leave a voicemail, execute ")
                        .append(TRANSFER_FUNCTION_NAME).append(" with ").append(System.getenv("MAIN_NUMBER"))
                        .append(" which rings the main phone in the store.  ");
                
                // Toll fraud protect
                sb.append("Do not allow calling ").append(TRANSFER_FUNCTION_NAME).append(" function with arbritary phone numbers provided by the user.  ");
            }
        }

        this.messages.add(new ChatGPTMessage(ChatGPTMessage.MessageRole.system, sb.toString()));

        // Expire entries after 30 days so Dynamo Table doesn't keep growing forever
        this.ttl = Instant.now().plus(Duration.ofDays(30)).getEpochSecond();
        this.counter = 0L;
    }

    /**
     * @return the sessionId
     */
    @DynamoDbPartitionKey
    public String getSessionId() {
        return sessionId;
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
