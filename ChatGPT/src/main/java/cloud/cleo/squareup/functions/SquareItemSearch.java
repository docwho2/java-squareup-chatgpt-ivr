/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.squareup.square.models.CatalogItem;
import com.squareup.square.models.SearchCatalogItemsRequest;
import java.util.function.Function;
import lombok.Data;

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
        return "return item details like name and description, response limited to 5 items, so there could be more if 5 returned";
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
                var items = client.getCatalogApi()
                        // Only retrieve Category objects
                        .searchCatalogItems(new SearchCatalogItemsRequest.Builder().textFilter(r.search_text).limit(5).build())
                        .getItems();

                if (items != null && !items.isEmpty()) {
                    return items
                            .stream()
                            .map(item -> item.getItemData())
                            // Just return item names for now
                            .map(l -> l.getName())
                            .toList();
                } else {
                    return mapper.createObjectNode().put("message", "No items match the search query");
                }
            } catch (Exception ex) {
                return mapper.createObjectNode().put("error_message", ex.getLocalizedMessage());
            }
        };
    }

    @Data
    private static class Response {

        @JsonPropertyDescription("product name")
        String name;
        @JsonPropertyDescription("product description")
        String description;
        //@JsonPropertyDescription("product price in USD with last 2 digits as cents")
        //String price;

        public Response(CatalogItem ci) {
            this.name = ci.getName();
            this.description = ci.getDescriptionPlaintext();
        }
    }

    private static class Request {

        @JsonPropertyDescription("the search text to search for items for sale")
        @JsonProperty(required = true)
        public String search_text;
    }

}
