package cloud.cleo.squareup.functions;

import cloud.cleo.squareup.LexInputMode;
import com.amazonaws.services.lambda.runtime.events.LexV2Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.square.Environment;
import com.squareup.square.SquareClient;
import com.theokanning.openai.completion.chat.ChatFunction;
import com.theokanning.openai.service.FunctionExecutor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;
import software.amazon.awssdk.services.pinpoint.PinpointClient;
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

    private static final List<AbstractFunction> functions = new LinkedList<>();
    private static boolean inited = false;

    /**
     * When user is interacting via Voice, we need the calling number to send SMS to them
     */
    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PRIVATE)
    private String callingNumber;

    /**
     * Define here since used by most the of the functions
     */
    protected final static SquareClient client = new SquareClient.Builder()
            .accessToken(System.getenv("SQUARE_API_KEY"))
            .environment(Environment.valueOf(System.getenv("SQUARE_ENVIRONMENT")))
            .build();

    public final static boolean squareEnabled;

    static {
        final var key = System.getenv("SQUARE_API_KEY");
        final var loc = System.getenv("SQUARE_LOCATION_ID");

        squareEnabled = !((loc == null || loc.isBlank() || loc.equalsIgnoreCase("DISABLED")) || (key == null || key.isBlank() || loc.equalsIgnoreCase("DISABLED")));
        log.debug("Square Enabled check = " + squareEnabled);
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
                    functions.add(func);
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
    public static FunctionExecutor getFunctionExecuter(LexV2Event lexRequest) {
        if (!inited) {
            init();
        }

        String callingNumber = null;

        if (lexRequest.getRequestAttributes() != null) {
            if (lexRequest.getRequestAttributes().containsKey("x-amz-lex:channels:platform")) {
                final var platform = lexRequest.getRequestAttributes().get("x-amz-lex:channels:platform");
                if (platform.contains("Chime")) {
                    // When chime calls us, the SMA will set "callingNumber" in the session
                    callingNumber = lexRequest.getSessionState().getSessionAttributes() != null
                            ? lexRequest.getSessionState().getSessionAttributes().get("callingNumber") : null;
                }
            }
            // Check for Integration Channels (For Twilio)
            if (lexRequest.getRequestAttributes().containsKey("x-amz-lex:channel-type")) {
                // All the channels will populate user-id and for Twilio this will be the callers phone number
                callingNumber = lexRequest.getRequestAttributes().get("x-amz-lex:user-id");
            }
        }

        final var fromNumber = callingNumber;

        final var inputMode = LexInputMode.fromString(lexRequest.getInputMode());
        final var list = new LinkedList<AbstractFunction>();

        functions.forEach(f -> {
            try {
                final var func = (AbstractFunction) f.clone();
                switch (inputMode) {
                    case TEXT -> {
                        if (func.isText()) {
                            list.add(func);
                        }
                    }
                    case SPEECH, DTMF -> {
                        if (func.isVoice()) {

                            list.add(func);
                        }
                    }
                }
                func.setCallingNumber(fromNumber);
            } catch (CloneNotSupportedException ex) {
                log.error("Error cloning Functions", ex);
            }
        });
        return new FunctionExecutor(list.stream().map(f -> f.getChatFunction()).toList());
    }

    /**
     * Name of the function
     *
     * @return
     */
    protected abstract String getName();

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
        if (callingNumber == null || callingNumber.isEmpty()) {
            return false;
        }
        return US_E164_PATTERN.matcher(callingNumber).matches();
    }

    /**
     * Store this in case we try and send SMS twice ever, don't want to pay for the lookup again since it costs money.
     * AWS usually calls the same Lambda, but anyways no harm to try and cache to save a couple cents here and there.
     */
    private static final Map<String,NumberValidateResponse> validatePhoneMap = new HashMap<>();

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
            if (! validatePhoneMap.containsKey(callingNumber)) {
                // First lookup, call pinpoint
                numberValidateResponse = PinpointClient.create()
                        .phoneNumberValidate(t -> t.numberValidateRequest(r -> r.isoCountryCode("US").phoneNumber(callingNumber)))
                        .numberValidateResponse();
                log.debug("Pinpoint returned " + numberValidateResponse.toBuilder().toString());
                // Add to cache
                validatePhoneMap.put(callingNumber, numberValidateResponse);
            } else {
                numberValidateResponse = validatePhoneMap.get(callingNumber);
                log.debug("Using cached Pinpoint response " + numberValidateResponse.toBuilder().toString());
            }
            // The description of the phone type. Valid values are: MOBILE, LANDLINE, VOIP, INVALID, PREPAID, and OTHER.
            return switch (numberValidateResponse.phoneType()) {
                case "MOBILE", "PREPAID" ->
                    true;
                default ->
                    false;
            };
        } catch (Exception e) {
            log.error("Error making pinpoint call", e);
            return false;
        }
    }

    /**
     * Override and return false to disable a particular function
     *
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
}
