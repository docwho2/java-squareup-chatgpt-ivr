package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.ChatGPTLambda.DRIVING_DIRECTIONS_FUNCTION_NAME;

/**
 * Base class for Driving Directions.
 *
 * @author sjensen
 * @param <Request>
 */
public abstract class DrivingDirections<Request> extends AbstractFunction {

    
     /**
     * URL for driving directions with Place ID so it comes up as Copper Fox Properly for the Pin
     */
    protected final static String DRIVING_DIRECTIONS_URL = "google.com/maps/dir/?api=1&destination=160+Main+St+Wahkon+MN+56386&destination_place_id=ChIJWxVcpjffs1IRcSX7D8pJSUY";

    
    @Override
    public final String getName() {
        return DRIVING_DIRECTIONS_FUNCTION_NAME;
    }

  

    @Override
    public final Class getRequestClass() {
        return Request.class;
    }

    protected final static class Request {
    }


}
