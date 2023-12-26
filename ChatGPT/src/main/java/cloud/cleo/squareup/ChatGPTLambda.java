package cloud.cleo.squareup;

import static cloud.cleo.squareup.enums.LexDialogAction.*;
import static cloud.cleo.squareup.enums.LexMessageContentType.*;
import cloud.cleo.squareup.functions.AbstractFunction;
import cloud.cleo.squareup.json.DurationDeserializer;
import cloud.cleo.squareup.json.DurationSerializer;
import cloud.cleo.squareup.json.LocalDateDeserializer;
import cloud.cleo.squareup.json.LocalDateSerializer;
import cloud.cleo.squareup.json.LocalTimeDeserializer;
import cloud.cleo.squareup.json.LocalTimeSerializer;
import cloud.cleo.squareup.json.ZoneIdDeserializer;
import cloud.cleo.squareup.json.ZonedSerializer;
import static cloud.cleo.squareup.lang.LangUtil.LanguageIds.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.LexV2Event;
import com.amazonaws.services.lambda.runtime.events.LexV2Event.Intent;
import com.amazonaws.services.lambda.runtime.events.LexV2Event.SessionState;
import com.amazonaws.services.lambda.runtime.events.LexV2Response;
import com.amazonaws.services.lambda.runtime.events.LexV2Response.Button;
import com.amazonaws.services.lambda.runtime.events.LexV2Response.ImageResponseCard;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.FunctionExecutor;
import com.theokanning.openai.service.OpenAiService;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/**
 *
 * @author sjensen
 */
public class ChatGPTLambda implements RequestHandler<LexV2Event, LexV2Response> {

    // Initialize the Log4j logger.
    Logger log = LogManager.getLogger(ChatGPTLambda.class);

    final static ObjectMapper mapper;

    final static TableSchema<ChatGPTSessionState> schema = TableSchema.fromBean(ChatGPTSessionState.class);

    // Create an AwsCrtAsyncHttpClient shared instance.
    public final static SdkAsyncHttpClient crtAsyncHttpClient = AwsCrtAsyncHttpClient.create();

    final static DynamoDbAsyncClient dynamoDbAsyncClient = DynamoDbAsyncClient.builder().httpClient(crtAsyncHttpClient).build();

    final static DynamoDbEnhancedAsyncClient enhancedClient = DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(dynamoDbAsyncClient).build();

    final static DynamoDbAsyncTable<ChatGPTSessionState> sessionState = enhancedClient.table(System.getenv("SESSION_TABLE_NAME"), schema);

    final static OpenAiService open_ai_service = new OpenAiService(System.getenv("OPENAI_API_KEY"), Duration.ofSeconds(50));
    final static String OPENAI_MODEL = System.getenv("OPENAI_MODEL");

    public final static String TRANSFER_FUNCTION_NAME = "transfer_call";
    public final static String HANGUP_FUNCTION_NAME = "hangup_call";
    public final static String FACEBOOK_HANDOVER_FUNCTION_NAME = "facebook_inbox";
    public final static String SWITCH_LANGUAGE_FUNCTION_NAME = "switch_language";

    // Eveverything here will be done at SnapStart init
    static {
        // Build up the mapper 
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // Add module for inputs
        SimpleModule module = new SimpleModule();
        // Serializers
        module.addSerializer(ZonedDateTime.class, new ZonedSerializer());
        module.addSerializer(LocalTime.class, new LocalTimeSerializer());
        module.addSerializer(LocalDate.class, new LocalDateSerializer());
        module.addSerializer(Duration.class, new DurationSerializer());

        // Deserializers for Input Types
        module.addDeserializer(LocalTime.class, new LocalTimeDeserializer());
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer());
        module.addDeserializer(ZoneId.class, new ZoneIdDeserializer());
        module.addDeserializer(Duration.class, new DurationDeserializer());

        mapper.registerModule(module);

        // Create and init all the functions in the package
        AbstractFunction.init();
        // Hit static initializers in this as well so it's loaded and hot
        new FaceBookOperations();
    }

    @Override
    public LexV2Response handleRequest(LexV2Event lexRequest, Context cntxt) {
        // Wrapped Event Class
        final LexV2EventWrapper event = new LexV2EventWrapper(lexRequest);
        try {
            log.debug(mapper.valueToTree(lexRequest).toPrettyString());
            // Intent which doesn't matter for us
            log.debug("Intent: " + event.getIntent());

            // For this use case, we only ever get the FallBack Intent, so the intent name means nothing here
            // We will process everythiung coming in as text to pass to GPT
            // IE, we are only using lex here to process speech and send it to us
            return switch (event.getIntent()) {
                default ->
                    processGPT(event);
            };

        } catch (CompletionException e) {
            log.error("Unhandled Future Exception", e.getCause());
            return buildResponse(new LexV2EventWrapper(lexRequest), event.getLangString(UNHANDLED_EXCEPTION));
        } catch (Exception e) {
            log.error("Unhandled Exception", e);
            // Unhandled Exception
            return buildResponse(new LexV2EventWrapper(lexRequest), event.getLangString(UNHANDLED_EXCEPTION));
        }
    }

    private LexV2Response processGPT(LexV2EventWrapper lexRequest) {
        var input = lexRequest.getInputTranscript();
        final var attrs = lexRequest.getSessionAttributes();
        // Will be phone if from SMS, Facebook the Page Scoped userID, Chime unique generated ID
        final var session_id = lexRequest.getSessionId();

        // For Voice we support 2 Locales, English and Spanish
        log.debug("Java Locale is " + lexRequest.getLocale());

        // Special Facebook Short Circut
        if (attrs.containsKey(FACEBOOK_HANDOVER_FUNCTION_NAME)) {
            log.debug("Facebook Short Circut, calling FB API to move thread to FB Inbox");
            FaceBookOperations.transferToInbox(session_id);
            // Clear out all sessions Attributes
            attrs.clear();
            // Send a close indicating we are done with this Lex Session
            return buildTerminatingResponse(lexRequest, FACEBOOK_HANDOVER_FUNCTION_NAME, Map.of(), "Thread moved to Facebook Inbox.");
        }

        if (input == null || input.isBlank()) {
            log.debug("Got blank input, so just silent or nothing");

            var count = Integer.valueOf(attrs.getOrDefault("blankCounter", "0"));
            count++;

            if (count > 2) {
                log.debug("Two blank responses, sending to Quit Intent");
                // Hang up on caller after 2 silience requests
                return buildTerminatingResponse(lexRequest, "hangup_call", Map.of(), lexRequest.getLangString(GOODBYE));
            } else {
                attrs.put("blankCounter", count.toString());
                // set the input as "blank" so GPT knows caller said nothing and may need suggestions
                input = "blank";
            }
        } else {
            // The Input is not blank, so always put the counter back to zero
            attrs.put("blankCounter", "0");
        }

        log.debug("Lex Session ID is " + session_id);

        // Key to record in Dynamo which we key by date.  So SMS/Facebook session won't span forever (by day)
        final var key = Key.builder().partitionValue(session_id).sortValue(LocalDate.now(ZoneId.of("America/Chicago")).toString()).build();

        //  load session state if it exists
        var session = sessionState.getItem(key).join();

        boolean session_new = false;
        if (session == null) {
            session = new ChatGPTSessionState(lexRequest);
            session_new = true;  // Track whether is new session so we can send welcome card for Facebook Channel
        }

        // add the user request to the session
        session.addUserMessage(input);

        String botResponse;
        // Store all the calls made
        List<ChatFunctionCall> functionCallsMade = new LinkedList<>();
        try {
            FunctionExecutor functionExecutor = AbstractFunction.getFunctionExecuter(lexRequest);
            functionExecutor.setObjectMapper(mapper);

            while (true) {
                final var chatMessages = session.getChatMessages();
                ChatCompletionRequest request = ChatCompletionRequest.builder()
                        .messages(chatMessages)
                        .model(OPENAI_MODEL)
                        .maxTokens(500)
                        .temperature(0.2) // More focused
                        .n(1) // Only return 1 completion
                        .functions(functionExecutor.getFunctions())
                        .functionCall(ChatCompletionRequest.ChatCompletionRequestFunctionCall.of("auto"))
                        .build();

                log.debug(chatMessages);
                log.debug("Start API Completion Call to ChatGPT");
                final var completion = open_ai_service.createChatCompletion(request);
                log.debug("End API Completion Call to ChatGPT");
                log.debug(completion);

                ChatMessage responseMessage = completion.getChoices().get(0).getMessage();
                botResponse = completion.getChoices().get(0).getMessage().getContent();

                // Add response to session
                session.addMessage(responseMessage);

                ChatFunctionCall functionCall = responseMessage.getFunctionCall();
                if (functionCall != null) {
                    log.debug("Trying to execute " + functionCall.getName() + "...");

                    Optional<ChatMessage> message = functionExecutor.executeAndConvertToMessageSafely(functionCall);

                    if (message.isPresent()) {
                        log.debug("Executed " + functionCall.getName() + ".");
                        session.addMessage(message.get());
                        // Track each call made
                        functionCallsMade.add(functionCall);
                        continue;
                    } else {
                        log.debug("Something went wrong with the execution of " + functionCall.getName() + "...");
                        try {
                            functionExecutor.executeAndConvertToMessage(functionCall);
                        } catch (Exception e) {
                            log.error("Funtion call error", e);
                            return buildResponse(lexRequest, "FunctionCall Error: " + e.getMessage());
                        }
                    }
                }
                break;
            }

            // Save the session to dynamo
            session.incrementCounter();
            sessionState.putItem(session).join();
        } catch (RuntimeException rte) {
            switch (rte.getCause()) {
                case SocketTimeoutException ste -> {
                    log.error("Response timed out", ste);
                    botResponse = lexRequest.getLangString(OPERATION_TIMED_OUT);
                }
                case null ->
                    throw rte;
                default ->
                    throw rte;
            }
        }

        log.debug("botResponse is [" + botResponse + "]");

        if (!functionCallsMade.isEmpty()) {
            // Did a terminating function get executed
            final var termCalled = functionCallsMade.stream()
                    .map(f -> AbstractFunction.getFunctionByName(f.getName()))
                    .filter(f -> f.isTerminating())
                    .findAny()
                    .orElse(null);
            if (termCalled != null) {
                log.debug("A termianting function was called = [" + termCalled.getName() + "]");
                final ChatFunctionCall gptFunCall = functionCallsMade.stream().filter(f -> f.getName().equals(termCalled.getName())).findAny().get();
                final var args = mapper.convertValue(gptFunCall.getArguments(), Map.class);
                return buildTerminatingResponse(lexRequest, gptFunCall.getName(), args, botResponse);
            } else {
                log.debug("The following funtion calls were made " + functionCallsMade + " but none are terminating");
            }

            // Special Facebook handoff check
            if (functionCallsMade.stream().anyMatch(f -> f.getName().equals(FACEBOOK_HANDOVER_FUNCTION_NAME))) {
                // Session needs to move to Inbox, but we can't do it now because then our response won't make it to end user
                // Push this into the Lex Session so on the next incoming message we can short circuit and call FB API
                attrs.put(FACEBOOK_HANDOVER_FUNCTION_NAME, "true");
                // Ignore what GPT said and send back message with Card asking how the bot did.
                return buildResponse(lexRequest, "ChatBot will be removed from this conversation after clicking below.", buildTransferCard());
            }
        }

        // Since we have a general response, add message asking if there is anything else
        //  For voice it just seems more natural to always end with a question.
        //if (lexRequest.isVoice() && !botResponse.endsWith("?")) {
            // If ends with question, then we don't need to further append question
            //botResponse = botResponse + lexRequest.getLangString(ANYTHING_ELSE);
        //}

        if (session_new && lexRequest.isFacebook()) {
            // If this a new Session send back a Welcome card for Facebook Channel
            // This works for Twilio/SMS, but sends a MMS and costs more money (it sends logo, but of course doesn't support the buttons)
            return buildResponse(lexRequest, botResponse, buildWelcomeCard());
        }

        // Default response from GPT that is not a terminating action
        return buildResponse(lexRequest, botResponse);
    }

    /**
     * Response that will tell Lex we are done so some action can be performed at the Chime Level (hang up, transfer,
     * MOH, etc.)
     *
     * @param lexRequest
     * @param transferNumber
     * @param botResponse
     * @return
     */
    private LexV2Response buildTerminatingResponse(LexV2EventWrapper lexRequest, String function_name, Map<String, String> functionArgs, String botResponse) {

        final var attrs = lexRequest.getSessionAttributes();

        // The controller (Chime SAM Lambda) will grab this from the session, then perform the terminating action
        attrs.put("action", function_name);
        attrs.put("bot_response", botResponse);
        attrs.putAll(functionArgs);

        // State to return
        final var ss = SessionState.builder()
                // Send all Session Attrs
                .withSessionAttributes(attrs)
                // We are always using Fallback, and let Lex know everything is fulfilled
                .withIntent(Intent.builder().withName("FallbackIntent").withState("Fulfilled").build())
                // Indicate we are closing things up, IE we are done here
                .withDialogAction(Close.getDialogAction())
                .build();

        final var lexV2Res = LexV2Response.builder()
                .withSessionState(ss)
                .build();
        log.debug("Response is " + mapper.valueToTree(lexV2Res));
        return lexV2Res;
    }

    /**
     * General Response used to send back a message and Elicit Intent again at LEX. IE, we are sending back GPT
     * response, and then waiting for Lex to collect speech and once again call us so we can send to GPT, effectively
     * looping until we call a terminating event like Quit or Transfer.
     *
     * @param lexRequest
     * @param response
     * @return
     */
    private LexV2Response buildResponse(LexV2EventWrapper lexRequest, String response, ImageResponseCard card) {

        final var messages = new LinkedList<LexV2Response.Message>();

        // Always send a plain text response
        //  If this is not first in the list, Lex will error
        messages.add(LexV2Response.Message.builder()
                .withContentType(PlainText.toString())
                .withContent(response)
                .build());

        if (card != null) {
            // Add a card if present
            messages.add(LexV2Response.Message.builder()
                    .withContentType(ImageResponseCard.toString())
                    .withImageResponseCard(card)
                    .build());
        }

        // State to return
        final var ss = SessionState.builder()
                // Retain the current session attributes
                .withSessionAttributes(lexRequest.getSessionAttributes())
                // Always ElictIntent, so you're back at the LEX Bot looking for more input
                .withDialogAction(ElicitIntent.getDialogAction())
                .build();

        final var lexV2Res = LexV2Response.builder()
                .withSessionState(ss)
                // List of messages to send back
                .withMessages(messages.toArray(LexV2Response.Message[]::new))
                .build();
        log.debug("Response is " + mapper.valueToTree(lexV2Res));
        return lexV2Res;
    }

    /**
     * Send a response without a card.
     *
     * @param lexRequest
     * @param response
     * @return
     */
    private LexV2Response buildResponse(LexV2EventWrapper lexRequest, String response) {
        return buildResponse(lexRequest, response, null);
    }

    /**
     * Welcome card which would be displayed for FaceBook Users in Messenger.
     *
     * @return
     */
    private ImageResponseCard buildWelcomeCard() {
        return com.amazonaws.services.lambda.runtime.events.LexV2Response.ImageResponseCard.builder()
                .withTitle("Welcome to Copper Fox Gifts")
                .withImageUrl("https://www.copperfoxgifts.com/logo.png")
                .withSubtitle("Ask us anything or use a quick action below")
                .withButtons(List.of(
                        Button.builder().withText("Hours").withValue("What are you business hours?").build(),
                        Button.builder().withText("Location").withValue("What is your address and driving directions?").build(),
                        Button.builder().withText("Person").withValue("Please hand this conversation over to a person").build()
                ).toArray(Button[]::new))
                .build();
    }

    /**
     * Transfer from Bot to Inbox Card for Facebook Messenger.
     *
     * @return
     */
    private ImageResponseCard buildTransferCard() {
        return com.amazonaws.services.lambda.runtime.events.LexV2Response.ImageResponseCard.builder()
                .withTitle("Conversation will move to Inbox")
                .withImageUrl("https://www.copperfoxgifts.com/logo.png")
                .withSubtitle("Please tell us how our AI ChatBot did?")
                .withButtons(List.of(
                        Button.builder().withText("Epic Fail").withValue("Chatbot was not Helpful.").build(),
                        Button.builder().withText("Needs Work").withValue("Chatbot needs some work.").build(),
                        Button.builder().withText("Great Job!").withValue("Chatbot did a great job!").build()
                ).toArray(Button[]::new))
                .build();
    }
}
