/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Function;

/**
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
        return "Return the store hours by day of week entries";
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
                final var locationsApi = client.getLocationsApi();
                return locationsApi.retrieveLocation(System.getenv("SQUARE_LOCATION_ID")).getLocation().getBusinessHours();
            } catch (Exception ex) {
                return new ObjectMapper().createObjectNode().put("error_message", ex.getLocalizedMessage());
            } 
        };
    }

    private static class Request {
    }

   

}
