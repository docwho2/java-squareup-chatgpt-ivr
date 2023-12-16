package cloud.cleo.squareup.functions;

import cloud.cleo.squareup.enums.ChannelPlatform;
import static cloud.cleo.squareup.ChatGPTLambda.crtAsyncHttpClient;
import cloud.cleo.squareup.LexV2EventWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.square.Environment;
import com.squareup.square.SquareClient;
import com.theokanning.openai.completion.chat.ChatFunction;
import com.theokanning.openai.service.FunctionExecutor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;
import software.amazon.awssdk.services.pinpoint.PinpointAsyncClient;
import software.amazon.awssdk.services.pinpoint.model.NumberValidateResponse;

/**
 * Base class for ChatGPT functions
 *
 * @author sjensen
 * @param <T>
 */
public abstract class AbstractFunction<T> implements Cloneable {

    // Initialize the Log4j logger.
    protected static final Logger log = LogManager.getLogger(AbstractFunction.class);

    protected final static ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, AbstractFunction> functions = new HashMap<>();
    private static boolean inited = false;

    private final static PinpointAsyncClient pinpointAsyncClient = PinpointAsyncClient.builder()
            .httpClient(crtAsyncHttpClient)
            .build();

    /**
     * When user is interacting via Voice, we need the calling number to send SMS to them.
     */
    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PRIVATE)
    private String callingNumber;

    /**
     * The Channel that is being used to interact with Lex. Will be UNKNOWN if no channel is set (like from Lex Console
     * or AWS CLI, etc.).
     */
    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PRIVATE)
    private ChannelPlatform channelPlatform;

    /**
     * The Lex Session ID.
     */
    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PRIVATE)
    private String sessionId;

    private final static boolean squareEnabled;
    private final static SquareClient squareClient;

    static {
        final var key = System.getenv("SQUARE_API_KEY");
        final var loc = System.getenv("SQUARE_LOCATION_ID");

        squareEnabled = !((loc == null || loc.isBlank() || loc.equalsIgnoreCase("DISABLED")) || (key == null || key.isBlank() || key.equalsIgnoreCase("DISABLED")));
        log.debug("Square Enabled = " + squareEnabled);

        // If square enabled, then configure the client
        if (squareEnabled) {
            squareClient = new SquareClient.Builder()
                    .accessToken(key)
                    .environment(Environment.valueOf(System.getenv("SQUARE_ENVIRONMENT")))
                    .build();
        } else {
            squareClient = null;
        }
    }

    /**
     * Is Square enabled (API Key and Location ID set to something that looks valid).
     *
     * @return
     */
    public final static boolean isSquareEnabled() {
        return squareEnabled;
    }

    /**
     * Square client to make API Calls.
     *
     * @return client or null if not enabled
     */
    protected final static SquareClient getSquareClient() {
        return squareClient;
    }

    /**
     * Register all the functions in this package. This should be called by a top level object that is being initialized
     * like a lambda, so during SNAPSTART init, all the functions will be inited as well.
     */
    public static void init() {
        if (inited) {
            return;  // only init once
        }

        // Use Reflections to get all classes in the package
        Reflections reflections = new Reflections(AbstractFunction.class.getPackage().getName());
        final var allClasses = reflections.getSubTypesOf(AbstractFunction.class);

        // Remove any further Abstract Classes
        allClasses.removeIf(c -> Modifier.isAbstract(c.getModifiers()));

        // Loop through each class and instantiate using the default constructor
        for (var clazz : allClasses) {
            try {
                final var func = (AbstractFunction) clazz.getDeclaredConstructor().newInstance();
                if (func.isEnabled()) {
                    log.debug("Instantiated class: " + clazz.getName());
                    functions.put(func.getName(), func);
                } else {
                    log.debug("Class Disabled, Ignoring: " + clazz.getName());
                }

            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                log.error("Error processing Function Classes", e);
            }
        }
        inited = true;
    }

    /**
     * Obtain an Executer for all the registered functions
     *
     * @param lexRequest
     * @return
     */
    public static FunctionExecutor getFunctionExecuter(LexV2EventWrapper lexRequest) {
        if (!inited) {
            init();
        }

        final String callingNumber = lexRequest.getPhoneE164();
        final ChannelPlatform channelPlatform = lexRequest.getChannelPlatform();

        final var list = new LinkedList<AbstractFunction>();
        final var sessionId = lexRequest.getSessionId();
        final var isText = lexRequest.isText();

        for (var f : functions.values()) {
            try {
                final var func = (AbstractFunction) f.clone();
                func.setCallingNumber(callingNumber);
                func.setChannelPlatform(channelPlatform);
                func.setSessionId(sessionId);
                if (isText) {
                    if (func.isText()) {
                        list.add(func);
                    }
                } else {
                    // If not Text, then this is voice of course
                    if (func.isVoice()) {
                        list.add(func);
                    }
                }
            } catch (CloneNotSupportedException ex) {
                log.error("Error cloning Functions", ex);
            }
        }
        return new FunctionExecutor(list.stream().map(f -> f.getChatFunction()).toList());
    }

    /**
     * Obtain function given it's name (null if not found).
     *
     * @param name
     * @return
     */
    public static AbstractFunction getFunctionByName(String name) {
        return functions.get(name);
    }

    /**
     * Name of the function
     *
     * @return
     */
    public abstract String getName();

    /**
     * Description for the function
     *
     * @return
     */
    protected abstract String getDescription();

    /**
     * The request class for this function
     *
     * @return
     */
    protected abstract Class<T> getRequestClass();

    /**
     * The Executer that will be run when the function is executed by the Executer
     *
     * @return
     */
    protected abstract Function<T, Object> getExecutor();

    /**
     * Build a ChatFunction Object
     *
     * @return
     */
    private ChatFunction getChatFunction() {
        return ChatFunction.builder()
                .description(getDescription())
                .name(getName())
                .executor(getRequestClass(), getExecutor())
                .build();
    }

    private static final Pattern US_E164_PATTERN = Pattern.compile("^\\+1[2-9]\\d{2}[2-9]\\d{6}$");

    /**
     * Is the callers number a valid US Phone number
     *
     * @return
     */
    protected boolean hasValidUSE164Number() {
        if (callingNumber == null || callingNumber.isBlank()) {
            return false;
        }
        return US_E164_PATTERN.matcher(callingNumber).matches();
    }

    /**
     * Store this in case we try and send SMS twice ever, don't want to pay for the lookup again since it costs money.
     * AWS usually calls the same Lambda, but anyways no harm to try and cache to save a couple cents here and there.
     */
    private static final Map<String, NumberValidateResponse> validatePhoneMap = new HashMap<>();

    /**
     * Is the callers number a valid Number we can send SMS to. We won't attempt to send to Voip or Landline callers
     *
     * @return
     */
    protected boolean hasValidUSMobileNumber() {
        if (!hasValidUSE164Number()) {
            return false;
        }
        try {
            NumberValidateResponse numberValidateResponse;
            log.debug("Validating " + callingNumber + "  with Pinpoint");
            if (!validatePhoneMap.containsKey(callingNumber)) {
                // First lookup, call pinpoint
                numberValidateResponse = pinpointAsyncClient
                        .phoneNumberValidate(t -> t.numberValidateRequest(r -> r.isoCountryCode("US").phoneNumber(callingNumber)))
                        .join().numberValidateResponse();
                log.debug("Pinpoint returned " + convertPinpointResposeToJson(numberValidateResponse));
                // Add to cache
                validatePhoneMap.put(callingNumber, numberValidateResponse);
            } else {
                numberValidateResponse = validatePhoneMap.get(callingNumber);
                log.debug("Using cached Pinpoint response " + convertPinpointResposeToJson(numberValidateResponse));
            }
            // The description of the phone type. Valid values are: MOBILE, LANDLINE, VOIP, INVALID, PREPAID, and OTHER.
            return switch (numberValidateResponse.phoneType()) {
                case "MOBILE", "PREPAID" ->
                    true;
                default ->
                    false;
            };
        } catch (CompletionException e) {
            log.error("Unhandled Error", e.getCause());
            return false;
        } catch (Exception e) {
            log.error("Error making pinpoint call", e);
            return false;
        }
    }

    private String convertPinpointResposeToJson(NumberValidateResponse res) {
        return mapper.valueToTree(mapper.convertValue(res.toBuilder(), NumberValidateResponse.serializableBuilderClass())).toPrettyString();
    }

    /**
     * Override and return false to disable a particular function.  This is only checked at function initialization time.
     * If you want to disable/enable at request time you can return false for both isVoice() and isText().  This is meant
     * to disable a function and leave the code laying around, or based on static initialized data.
     * @see isVoice()
     * @see isText()
     * @return
     */
    protected boolean isEnabled() {
        return true;
    }

    /**
     * Provide and enable this function for voice calls. Override to disable.
     *
     * @return
     */
    protected boolean isVoice() {
        return true;
    }

    /**
     * Provide and enable this function for text interactions. Override to disable.
     *
     * @return
     */
    protected boolean isText() {
        return true;
    }

    /**
     * When this function is called, will this result in ending the current session and returning control back to Chime.
     * IE, hang up, transfer, etc. This should all be voice related since you never terminate a text session, lex will
     * time it out based on it's setting.
     *
     * @return
     */
    public boolean isTerminating() {
        return false;
    }
}
