/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.ChatGPTLambda.WEBSITE_URL;


/**
 *
 * @author sjensen
 * @param <Request>
 */
public abstract class PrivateShoppingLink<Request> extends AbstractFunction {
    
    public final static String PRIVATE_SHOPPING_URL = WEBSITE_URL + "/book";

  

    @Override
    public final Class getRequestClass() {
        return Request.class;
    }

    protected final static class Request {
    }
    
}
