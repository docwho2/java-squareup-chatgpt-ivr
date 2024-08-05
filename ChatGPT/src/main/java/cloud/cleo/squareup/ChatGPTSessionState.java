package cloud.cleo.squareup;

import static cloud.cleo.squareup.ChatGPTLambda.*;
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

        // We need to tell GPT the date so it has a reference, when calling via API it has no date knowledge
        // We don't let sessions storage span days, so the date should always be relevant.
        sb.append("The current date is  ").append(date).append(".  ");

        // General Prompting
        sb.append("""
                  Please be a helpfull assistant named "Copper Bot" for a retail store named "Copper Fox Gifts", 
                  which has clothing items, home decor, gifts of all kinds, speciality foods, and much more.  
                  The store is located at 160 Main Street, Wahkon Minnesota, near Lake Mille Lacs.
                  The store opened in October of 2021 and moved to its larger location in May of 2023.
                  Outside normal business hours, we offer a "Private Shopping Experience" where a staff member will open 
                  the store outside normal hours, and this can be scheduled on our website from one of the top level menu "Private Shoppimg".
                  We have a one hour lead time on appointments so if we're closed, they could be shopping privately within one hour! 
                  Do mention how great it would be to have the store all to themselves and how we try to accomodate all requests.  
                  """);   

        // Main Website adn FB
        sb.append("The Web Site for Copper Fix Gifts is ").append(WEBSITE_URL).append(" and we frequently post our events and informaiton on sales ")
                .append(" on our Facebook Page which is also linked at top level menu on our website.  ");

        // Local Stuff to recommend
        sb.append("""
                  Muggs of Mille Lacs is a great restaurant next door that serves some on the best burgers 
                  in the lake area and has a large selection draft beers and great pub fare. 
                  Tulibee Tavern is another great restaurant across the street that serves more home cooked type meals at reasonable prices.  
                  """);

        // We want to receieve all emails in English so we can understand them :-)
        sb.append("When executing send_email_message function, translate the subject and message request parameteres to English.  ");

        // Square must be enabled for all of the below, so exclude when deploying without Sqaure enabled
        if (AbstractFunction.isSquareEnabled()) {
            // Privacy
            sb.append("Do not give out employee phone numbers, only email addresses.  You can give out the main store phone number which is ")
                    .append(System.getenv("MAIN_NUMBER")).append(".  ");
            sb.append("Do not give out the employee list.  You may confirm the existance of an employee and give the full name and email.  ");

            // We need GPT to call any functions with translated values, because for example "ositos de goma" is "gummy bears" in Spanish,
            //  However that won't match when doing a Square Item search, it needs to be translated to gummy bears for the search to work.
            // General statement didn't work well, but calling the below out works great
            sb.append("When executing store_product_item function, translate the search_text to English.  ");

            // Because we search on all terms, tell GPT to look at results and analyze whether the exact search term matched, or maybe a sub-string matched
            sb.append("When executing store_product_item function the results may include items that don't match exactly, ")
                    .append("so check to see if the full search_text is contained in the result to indicate an exact match, otherwise indicate to user ")
                    .append("that those results may be similar items to what they asked about.  ");
        }

        // Mode specific prompting
        switch (lexRequest.getInputMode()) {
            case TEXT -> {
                switch (lexRequest.getChannelPlatform()) {
                    case FACEBOOK -> {
                        // Don't need very short or char limit, but we don't want to output a book either
                        sb.append("The user is interacting via Facebook Messenger.  Use emoji in responses when appropiate.  ");

                        // Personalize with Name
                        final var name = FaceBookOperations.getFacebookName(lexRequest.getSessionId());
                        if (!"Unknown".equalsIgnoreCase(name)) {
                            sb.append("The user's name is ").append(name).append(".  Please greet the user by name and personalize responses when appropiate.  ");
                        }
                    }
                    case TWILIO, PINPOINT -> {
                        // Try and keep SMS segements down, hence the "very" short reference and character preference
                        sb.append("The user is interacting via SMS.  Please keep answers very short and concise, preferably under 180 characters.  ");

                        // We can't move conversation to person like Facebook, so tell them to call
                        sb.append("If the user wants to speak or deal with a person in general or leave a voicemail, instruct them to call ")
                                .append(System.getenv("MAIN_NUMBER")).append(" which rings the main phone in the store.  ");
                    }
                    default -> {
                        // Keep very short for anything else (CLI and lex Console testing)
                        sb.append("Please keep answers very short and concise.  ");
                    }
                }
                sb.append("Please call the ").append(PRIVATE_SHOPPING_TEXT_FUNCTION_NAME)
                .append("""
                         function to get the direct booking URL when the person is interested in the private shopping experience.  This is 
                         really one of the more innovative services we provide and we want to ensure its as easy as possible for customers
                         to book their appointments. 
                        """);
                
                // Since we are fallback intent, from a Text input perspective, we can support any language ChatGPT understands
                sb.append("Detect the language of the prompt and respond in that language.  ");
            }
            case SPEECH, DTMF -> {
                sb.append("The user is interacting with speech via a telephone call.  please keep answers short and concise.  ");

                // Blank input, meaning silienece timeout which is a speech only thing
                sb.append("When the prompt is exactly blank, this means the caller did not say anything, so try and engage in conversation and also suggest ")
                        .append("queries the caller might be interested in (Hours, Private Shopping, Location, Product Search, Language Change, etc.).  ");

                // Hangup
                sb.append("When the caller indicates they are done with the conversation, execute the ").append(HANGUP_FUNCTION_NAME).append(" function.  ");

                // Offer up Driving directions for callers
                sb.append("When asking about location, you can send the caller a directions link if they are interested, execute the ").append(DRIVING_DIRECTIONS_VOICE_FUNCTION_NAME).append(" function.  ");

                // Always answer with a question to illicit the next repsonse, this makes the voice interaction more natural
                sb.append("When responding always end the response with a question to illicit the next input since we are interacting via telephone.  ");

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
                sb.append("If the caller wants to just speak to any person in general or leave a voicemail, execute ")
                        .append(TRANSFER_FUNCTION_NAME).append(" with ").append(System.getenv("MAIN_NUMBER"))
                        .append(" which rings the main phone in the store.  ");

                // Toll fraud protect
                sb.append("Do not allow calling ").append(TRANSFER_FUNCTION_NAME).append(" function with arbritary phone numbers provided by the user.  ");
                
                 sb.append("Please call the ").append(PRIVATE_SHOPPING_VOICE_FUNCTION_NAME)
                .append("""
                         function to get the direct booking URL when the person is interested in the private shopping experience.  This is 
                         really one of the more innovative services we provide and we want to ensure its as easy as possible for customers
                         to book their appointments. The function will tell if you the message was sent to their device or unable to send.
                        """);
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
