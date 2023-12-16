package cloud.cleo.squareup.functions;

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
        return "driving_directions";
    }

  

    @Override
    public final Class getRequestClass() {
        return Request.class;
    }

    protected final static class Request {
    }


}
