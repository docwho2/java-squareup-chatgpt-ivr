package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.ChatGPTLambda.DRIVING_DIRECTIONS_TEXT_FUNCTION_NAME;
import java.util.function.Function;

/**
 * Driving Directions when user is interacting via Text interface.
 * 
 * @author sjensen
 */
public class DrivingDirectionsText extends DrivingDirections {

    
     @Override
    public final String getName() {
        return DRIVING_DIRECTIONS_TEXT_FUNCTION_NAME;
    }

  
    
    @Override
    protected String getDescription() {
        return "Returns a URL for Driving directions to the Store";
    }

    @Override
    protected Function getExecutor() {
        return (var r) -> {
            return mapper.createObjectNode().put("url", DRIVING_DIRECTIONS_URL);
        };
    }

    /**
     * This function is Text only
     * @return 
     */
    @Override
    protected boolean isVoice() {
        return false;
    }
    
    @Override
    protected boolean isText() {
        return true;
    }

}
