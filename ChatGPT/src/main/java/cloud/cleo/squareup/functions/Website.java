/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

/**
 *
 * @author sjensen
 * @param <Request>
 */
public abstract class Website<Request> extends AbstractFunction {
    
    protected final static String WEBSITE_URL = "www.CopperFoxGifts.com";
    
    @Override
    public final String getName() {
        return "store_website";
    }

  

    @Override
    public final Class getRequestClass() {
        return Request.class;
    }

    protected final static class Request {
    }
    
}
