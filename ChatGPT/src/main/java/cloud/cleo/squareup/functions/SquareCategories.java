/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.squareup.square.models.CatalogQuery;
import com.squareup.square.models.CatalogQueryText;
import com.squareup.square.models.SearchCatalogObjectsRequest;
import java.util.List;
import java.util.function.Function;

/**
 * Provide the categories, so callers know if we carry those types of products
 *
 * @author sjensen
 * @param <Request>
 */
public class SquareCategories<Request> extends AbstractFunction {

    @Override
    public String getName() {
        return "store_product_categories";
    }

    @Override
    public String getDescription() {
        return "Return the product categories available for sale in the store based on a search word";
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
                final var objects = client.getCatalogApi()
                        // Only retrieve Category objects
                        .searchCatalogObjects(new SearchCatalogObjectsRequest.Builder()
                                .includeDeletedObjects(false)
                                .objectTypes(List.of("CATEGORY"))
                                .query(new CatalogQuery.Builder().textQuery(new CatalogQueryText(List.of(r.search_text))).build())
                                .build())
                        .getObjects();

                if (objects != null && !objects.isEmpty()) {
                    return objects.stream()
                            // Map these down to just the cat name
                            .map(cat -> cat.getCategoryData().getName())
                            .toList();
                } else {
                    return mapper.createObjectNode().put("message", "No categories match the search query");
                }
            } catch (Exception ex) {
                return mapper.createObjectNode().put("error_message", ex.getLocalizedMessage());
            }
        };
    }

    private static class Request {

        @JsonPropertyDescription("the search text to search for item categories")
        @JsonProperty(required = true)
        public String search_text;
    }
    
      
    @Override
    protected boolean isEnabled() {
        return squareEnabled;
    }

}
