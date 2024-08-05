/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.ChatGPTLambda.PRIVATE_SHOPPING_TEXT_FUNCTION_NAME;
import java.util.function.Function;

/**
 *
 * @author sjensen
 */
public class PrivateShoppingLinkText extends PrivateShoppingLink {
    
    @Override
    public final String getName() {
        return PRIVATE_SHOPPING_TEXT_FUNCTION_NAME;
    }
    
    @Override
    protected String getDescription() {
        return "Returns a URL for direct booking of Private Shopping";
    }

    @Override
    protected Function getExecutor() {
        return (var r) -> {
            return mapper.createObjectNode().put("url", PRIVATE_SHOPPING_URL);
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
    
    /**
     * 
     *
     * @return
     */
    @Override
    protected boolean isText() {
        return true;
    }
}
