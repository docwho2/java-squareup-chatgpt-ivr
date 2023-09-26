/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

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

    private static final List<AbstractFunction> functions = new LinkedList<>();
    private static boolean inited = false;

    protected final static SquareClient client = new SquareClient.Builder()
            .accessToken(System.getenv("SQUARE_API_KEY"))
            .environment(Environment.PRODUCTION)
            .build();

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
                    functions.add(func);
                }
                System.out.println("Instantiated class: " + clazz.getName());
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        inited = true;
    }

    /**
     * Obtain an Executer for all the registered functions
     *
     * @param user
     * @return
     */
    public static FunctionExecutor getFunctionExecuter() {
        if (!inited) {
            init();
        }

        final var list = new LinkedList<AbstractFunction>();

        functions.forEach(f -> {
            try {
                final var func = (AbstractFunction) f.clone();

                list.add(func);
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
}
