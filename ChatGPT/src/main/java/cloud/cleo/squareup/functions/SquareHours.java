/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

import java.util.function.Function;

/**
 *  Return the store hours from Square API
 * 
 * @author sjensen
 * @param <Request>
 */
public class SquareHours<Request> extends AbstractFunction {


    @Override
    public String getName() {
        return "store_hours";
    }

    @Override
    public String getDescription() {
        return "Return the open store hours by day of week, any day of week not returned means store is closed that day.";
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
            try {
                return client.getLocationsApi()
                        .retrieveLocation(System.getenv("SQUARE_LOCATION_ID"))
                        .getLocation().getBusinessHours();
            } catch (Exception ex) {
                return mapper.createObjectNode().put("error_message", ex.getLocalizedMessage());
            } 
        };
    }

    private static class Request {
    }

   

}
