/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.squareup.square.models.SearchCatalogItemsRequest;
import java.util.function.Function;

/**
 * Search for items based on search query
 *
 * @author sjensen
 * @param <Request>
 */
public class SquareItemSearch<Request> extends AbstractFunction {

    @Override
    public String getName() {
        return "store_product_item";
    }

    @Override
    public String getDescription() {
        return "return item names, response limited to 5 items, so there could be more if 5 returned";
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
                var items = getSquareClient().getCatalogApi()
                        .searchCatalogItems(new SearchCatalogItemsRequest.Builder().textFilter(r.search_text).limit(5).build())
                        .getItems();

                if (items != null && !items.isEmpty()) {
                    return items
                            .stream()
                            .map(item -> item.getItemData())
                            // Just return item names 
                            .map(l -> l.getName())
                            .toList();
                } else {
                    return mapper.createObjectNode().put("message", "No items match the search query");
                }
            } catch (Exception ex) {
                log.error("Unhandled Error",ex);
                return mapper.createObjectNode().put("error_message", ex.getLocalizedMessage());
            }
        };
    }

    private static class Request {

        @JsonPropertyDescription("the search text to search for items for sale")
        @JsonProperty(required = true)
        public String search_text;
    }
    
    @Override
    protected boolean isEnabled() {
        return isSquareEnabled();
    }

}
