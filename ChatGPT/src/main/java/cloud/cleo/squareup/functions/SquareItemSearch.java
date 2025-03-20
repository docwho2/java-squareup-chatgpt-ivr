package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.functions.AbstractFunction.log;
import static cloud.cleo.squareup.functions.AbstractFunction.mapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.squareup.square.types.SearchCatalogItemsRequest;
import com.squareup.square.types.SearchCatalogItemsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Search for items based on search query using Virtual Threads.
 *
 * @author sjensen
 */
public class SquareItemSearch<Request> extends AbstractFunction {

    @Override
    public String getName() {
        return "store_product_item";
    }

    @Override
    public String getDescription() {
        return "Return item names, response limited to 5 items, so there could be more if 5 returned";
    }

    @Override
    public Class getRequestClass() {
        return Request.class;
    }

    /**
     * Executes the search request using virtual threads.
     *
     * @return Function<Request, Object>
     */
    @Override
    public Function<Request, Object> getExecutor() {
        return (var r) -> {
            List<String> tokens = allCombinations(r.search_text);
            List<String> itemNames = new ArrayList<>();

            log.debug("Launching {} item searches in parallel using virtual threads", tokens.size());

            // Use virtual threads for parallel execution
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<SearchCatalogItemsResponse>> futures = tokens.stream()
                        .map(token -> executor.submit(() -> {
                            log.debug("Executing search for [{}]", token);
                            return getSquareClient().catalog()
                                    .searchItems(SearchCatalogItemsRequest.builder()
                                            .textFilter(token)
                                            .limit(5)
                                            .build())
                                    .join(); // Block only inside the virtual thread
                        }))
                        .collect(Collectors.toList());

                // Collect results
                for (Future<SearchCatalogItemsResponse> future : futures) {
                    try {
                        SearchCatalogItemsResponse response = future.get(); // Blocking only inside virtual threads
                        if (response.getItems() != null) {
                            response.getItems().get().stream()
                                    .map(item -> item.getItem().get().getItemData().get().getName().get())
                                    .forEach(itemNames::add);
                        }
                    } catch (Exception e) {
                        log.error("Error processing search request", e);
                    }
                }
            } catch (Exception ex) {
                log.error("Unhandled Error", ex);
                return mapper.createObjectNode().put("status", "FAILED").put("error_message", ex.getLocalizedMessage());
            }

            if (itemNames.isEmpty()) {
                return mapper.createObjectNode().put("message", "No items match the search query");
            } else {
                return itemNames.stream().distinct().limit(5).toList();
            }
        };
    }

    private static class Request {
        @JsonPropertyDescription("The search text to search for items for sale in English language")
        @JsonProperty(required = true)
        public String search_text;
    }

    @Override
    protected boolean isEnabled() {
        return isSquareEnabled();
    }
}