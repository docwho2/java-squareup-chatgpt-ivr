/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup;

import cloud.cleo.squareup.enums.LexInputMode;
import static cloud.cleo.squareup.enums.LexInputMode.TEXT;
import cloud.cleo.squareup.functions.AbstractFunction;
import cloud.cleo.squareup.json.DurationDeserializer;
import cloud.cleo.squareup.json.DurationSerializer;
import cloud.cleo.squareup.json.LocalDateDeserializer;
import cloud.cleo.squareup.json.LocalDateSerializer;
import cloud.cleo.squareup.json.LocalTimeDeserializer;
import cloud.cleo.squareup.json.LocalTimeSerializer;
import cloud.cleo.squareup.json.ZoneIdDeserializer;
import cloud.cleo.squareup.json.ZonedSerializer;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.LexV2Event;
import com.amazonaws.services.lambda.runtime.events.LexV2Event.DialogAction;
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

    public final static String GENERAL_ERROR_MESG = "Sorry, I'm having a problem fulfilling your request. Please try again later.";

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
    }

    @Override
    public LexV2Response handleRequest(LexV2Event lexRequest, Context cntxt) {

        try {
            log.debug(mapper.valueToTree(lexRequest).toString());
            final var intentName = lexRequest.getSessionState().getIntent().getName();
            log.debug("Intent: " + intentName);

            // For this use case, we only ever get the FallBack Intent, so the intent name means nothing here
            // We will process everythiung coming in as text to pass to GPT
            // IE, we are only using lex here to process speech and send it to us
            return switch (intentName) {
                default ->
                    processGPT(lexRequest);
            };

        } catch (CompletionException e) {
            log.error("Unhandled Future Exception", e.getCause());
            return buildResponse(lexRequest, GENERAL_ERROR_MESG);
        } catch (Exception e) {
            log.error("Unhandled Exception", e);
            // Unhandled Exception
            return buildResponse(lexRequest, GENERAL_ERROR_MESG);
        }
    }

    private LexV2Response processGPT(LexV2Event lexRequest) {
        final var input = lexRequest.getInputTranscript();
        final var localId = lexRequest.getBot().getLocaleId();
        final var inputMode = LexInputMode.fromString(lexRequest.getInputMode());
        final var attrs = lexRequest.getSessionState().getSessionAttributes();

        log.debug("Java Locale is " + localId);

        if (input == null || input.isBlank()) {
            log.debug("Got blank input, so just silent or nothing");

            var count = Integer.valueOf(attrs.getOrDefault("blankCounter", "0"));
            count++;

            if (count > 2) {
                log.debug("Two blank responses, sending to Quit Intent");
                // Hang up on caller after 2 silience requests
                return buildTerminatingResponse(lexRequest, "hangup_call", Map.of(), "Thank you for calling, goodbye.");
            } else {
                attrs.put("blankCounter", count.toString());
                // If we get slience (timeout without speech), then we get empty string on the transcript
                return buildResponse(lexRequest, "I'm sorry, I didn't catch that, if your done, simply say good bye, otherwise tell me how I can help?");
            }
        } else {
            // The Input is not blank, so always put the counter back to zero
            attrs.put("blankCounter", "0");
        }

        // We could use phone Number coming in from Chime so that you could call back and keep session going between calls,
        // But using lex sessionId as the key makes each phone call unique and more applicable to this use case
        final var session_id = lexRequest.getSessionId();

        // Key to record in Dynamo
        final var key = Key.builder().partitionValue(session_id).sortValue(LocalDate.now(ZoneId.of("America/Chicago")).toString()).build();

        //  load session state if it exists
        var session = sessionState.getItem(key).join();

        if (session == null) {
            session = new ChatGPTSessionState(session_id, inputMode);
        }

        // Since we can call and change language during session, always specifiy how we want responses
        //session.addSystemMessage(lang.getString(CHATGPT_RESPONSE_LANGUAGE));
        // add this request to the session
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
                    /* You can also try 'executeAndConvertToMessage' inside a try-catch block, and add the following line inside the catch:
                "message = executor.handleException(exception);"
                The content of the message will be the exception itself, so the flow of the conversation will not be interrupted, and you will still be able to log the issue. */

                    if (message.isPresent()) {
                        /* At this point:
                    1. The function requested was found
                    2. The request was converted to its specified object for execution (Weather.class in this case)
                    3. It was executed
                    4. The response was finally converted to a ChatMessage object. */

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
            if (rte.getCause() != null && rte.getCause() instanceof SocketTimeoutException) {
                log.error("Response timed out", rte);
                botResponse = "The operation timed out, please ask your question again";
            } else {
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
        }

        // Since we have a general response, add message asking if there is anything else
        if (!TEXT.equals(inputMode)) {
            // Only add if not text (added to voice response)
            if (!botResponse.endsWith("?")) {  // If ends with question, then we don't need to further append question
                botResponse = botResponse + "  What else can I help you with?";
            }
        }

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
    private LexV2Response buildTerminatingResponse(LexV2Event lexRequest, String function_name, Map<String, String> functionArgs, String botResponse) {

        final var attrs = lexRequest.getSessionState().getSessionAttributes();

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
                .withDialogAction(DialogAction.builder().withType("Close").build())
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
    private LexV2Response buildResponse(LexV2Event lexRequest, String response ) {
        
        // State to return
        final var ss = SessionState.builder()
                // Retain the current session attributes
                .withSessionAttributes(lexRequest.getSessionState().getSessionAttributes())
                // Always ElictIntent, so you're back at the LEX Bot looking for more input
                .withDialogAction(DialogAction.builder().withType("ElicitIntent").build())
                .build();

        final var lexV2Res = LexV2Response.builder()
                .withSessionState(ss)
                // We are using plain text responses
                .withMessages(new LexV2Response.Message[]{new LexV2Response.Message("PlainText", response, buildCard())})
                .build();
        log.debug("Response is " + mapper.valueToTree(lexV2Res));
        return lexV2Res;
    }

    private ImageResponseCard buildCard() {
        return ImageResponseCard.builder()
                .withTitle("Some things to try")
                .withSubtitle("Choose or ask Copper Fox anything")
                .withButtons(List.of(
                        Button.builder().withText("Hours").withValue("What are you business hours?").build(),
                        Button.builder().withText("Location").withValue("What is your address and URL for driving directions?").build(),
                        Button.builder().withText("Person").withValue("Please hand this conversation over to a person").build()
                ).toArray(Button[]::new))
                .build();
    }
}
