package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.functions.AbstractFunction.allCombinations;
import static cloud.cleo.squareup.functions.AbstractFunction.getSquareClient;
import static cloud.cleo.squareup.functions.AbstractFunction.log;
import static cloud.cleo.squareup.functions.AbstractFunction.mapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.squareup.square.types.CatalogObjectType;
import com.squareup.square.types.CatalogQuery;
import com.squareup.square.types.CatalogQueryText;
import com.squareup.square.types.SearchCatalogObjectsRequest;
import com.squareup.square.types.SearchCatalogObjectsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provide the categories, so callers know if we carry those types of products.
 * Uses Virtual Threads instead of CompletableFuture.
 *
 * @author sjensen
 */
@Deprecated
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
     * Executes the category search using virtual threads.
     *
     * @return Function<Request, Object>
     */
    @Override
    public Function<Request, Object> getExecutor() {
        return (var r) -> {
            List<String> tokens = allCombinations(r.search_text);
            List<String> catNames = new ArrayList<>();

            log.debug("Launching {} category searches in parallel using virtual threads", tokens.size());

            try (ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<SearchCatalogObjectsResponse>> futures = tokens.stream()
                        .map(token -> executor.submit(() -> {
                            log.debug("Executing category search for [{}]", token);
                            return getSquareClient().catalog()
                                    .search(SearchCatalogObjectsRequest.builder()
                                            .includeDeletedObjects(false)
                                            .objectTypes(List.of(CatalogObjectType.CATEGORY))
                                            .query(CatalogQuery.builder().textQuery( CatalogQueryText.builder().addKeywords(token).build()).build())
                                            .build())
                                    .join(); // Block only inside the virtual thread
                        }))
                        .collect(Collectors.toList());

                // Collect results
                for (Future<SearchCatalogObjectsResponse> future : futures) {
                    try {
                        SearchCatalogObjectsResponse response = future.get(); // Blocking only inside virtual threads
                        if (response.getObjects() != null) {
                            response.getObjects().get().stream()
                                    .map(item -> item.getCategory().get().getCategoryData().get().getName().get())
                                    .forEach(catNames::add);
                        }
                    } catch (Exception e) {
                        log.error("Error processing category search request", e);
                    }
                }
            } catch (Exception ex) {
                log.error("Unhandled Error", ex);
                return mapper.createObjectNode().put("status", "FAILED").put("error_message", ex.getLocalizedMessage());
            }

            if (!catNames.isEmpty()) {
                return catNames.stream().distinct().limit(5).toList();
            } else {
                return mapper.createObjectNode().put("message", "No categories match the search query");
            }
        };
    }

    private static class Request {
        @JsonPropertyDescription("The search text to search for item categories in English Language")
        @JsonProperty(required = true)
        public String search_text;
    }

    @Override
    protected boolean isEnabled() {
        return false;  // Not using categories anymore, didn't seem to help much
    }
}