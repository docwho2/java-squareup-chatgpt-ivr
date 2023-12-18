
package cloud.cleo.squareup.functions;


import static cloud.cleo.squareup.ChatGPTLambda.SWITCH_LANGUAGE_FUNCTION_NAME;
import cloud.cleo.squareup.enums.Language;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.function.Function;

/**
 *  Exit current bot context and come back with a new locale (language)
 * 
 * @author sjensen
 * @param <Request>
 */
public class SwitchLanguage<Request> extends AbstractFunction {


    @Override
    public String getName() {
        return SWITCH_LANGUAGE_FUNCTION_NAME;
    }

    @Override
    public String getDescription() {
        return "Change the Language to interact with";
    }

    @Override
    public Class getRequestClass() {
        return Request.class;
    }

    /**
     *
     * @return
     */
    @Override
    public Function<Request, Object> getExecutor() {
        return (var r) -> {
            return mapper.createObjectNode().put("message", "The caller is now ready to interact in " + r.language);
        };
    }

    private static class Request {
        @JsonPropertyDescription("The language to switch to")
        @JsonProperty(value = "language",required = true)
        public Language language;
    }

    
   /**
     * Language not applicable when using a text interface
     * (language is fixed because each Channel is linked to a locale in Lex console)
     * 
     * @return 
     */
    @Override
    protected boolean isText() {
        return false;
    }
    
    /**
     * Call leaves GPT and back to Chime (which will call other Bot Locale).  Voice Only.
     * @return 
     */
    @Override
    public boolean isTerminating() {
        return true;
    }

}
