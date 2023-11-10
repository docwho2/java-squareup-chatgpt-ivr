package cloud.cleo.squareup.functions;

import cloud.cleo.squareup.LexInputMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.square.Environment;
import com.squareup.square.SquareClient;
import com.theokanning.openai.completion.chat.ChatFunction;
import com.theokanning.openai.service.FunctionExecutor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;

/**
 * Base class for ChatGPT functions
 *
 * @author sjensen
 * @param <T>
 */
public abstract class AbstractFunction<T> implements Cloneable {

    // Initialize the Log4j logger.
    protected Logger log = LogManager.getLogger();
    
    protected final static ObjectMapper mapper = new ObjectMapper();

    private static final List<AbstractFunction> functions = new LinkedList<>();
    private static boolean inited = false;

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
        
        squareEnabled =  !((loc == null || loc.isBlank()) || (key == null || key.isBlank()));
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

        // Loop through each class and instantiate using the default constructor
        for (var clazz : allClasses) {
            try {
                final var func = (AbstractFunction) clazz.getDeclaredConstructor().newInstance();
                if (func.isEnabled()) {
                    System.out.println("Instantiated class: " + clazz.getName());
                    functions.add(func);
                } else {
                     System.out.println("Class Disabled, Ignoring: " + clazz.getName());
                }
                
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        inited = true;
    }

    /**
     * Obtain an Executer for all the registered functions
     *
     * @param inputMode
     * @return
     */
    public static FunctionExecutor getFunctionExecuter(LexInputMode inputMode) {
        if (!inited) {
            init();
        }

        final var list = new LinkedList<AbstractFunction>();

        functions.forEach(f -> {
            try {
                final var func = (AbstractFunction) f.clone();
                switch(inputMode) {
                    case TEXT -> {
                        if ( func.isText() ) {
                            list.add(func);
                        }
                    }
                    case SPEECH, DTMF -> {
                        if ( func.isVoice() ) {
                            list.add(func);
                        }
                    }    
                }
                
            } catch (CloneNotSupportedException ex) {
                ex.printStackTrace();
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

    /**
     * Override and return false to disable a particular function
     *
     * @return
     */
    protected boolean isEnabled() {
        return true;
    }
    
    /**
     * Provide and enable this function for voice calls.  Override to disable.
     * @return 
     */
    protected boolean isVoice() {
        return true;
    }
    
    /**
     * Provide and enable this function for text interactions.  Override to disable.
     * @return 
     */
    protected boolean isText() {
        return true;
    }
}
