/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.functions.AbstractFunction.log;
import static cloud.cleo.squareup.functions.AbstractFunction.mapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.squareup.square.models.SearchCatalogItemsRequest;
import com.squareup.square.models.SearchCatalogItemsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
                // Generate a list of search tokens with every combination of the words
                final List<String> tokens = allCombinations(r.search_text);

                // List to hold all the search futures
                final List<CompletableFuture<SearchCatalogItemsResponse>> futures = new ArrayList<>(tokens.size());

                // Launch all the searches
                for (final var token : tokens) {
                    futures.add(getSquareClient().getCatalogApi()
                            .searchCatalogItemsAsync(new SearchCatalogItemsRequest.Builder().textFilter(token).limit(5).build()));
                    log.debug("Launching Item Search on [{}]", token);
                }

                log.debug("Starting wait on {} futures", futures.size());
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                log.debug("All Futures completed, processing results");

                // Item names to return to GPT
                final List<String> itemNames = new ArrayList<>();

                // Process the results
                for (final var future : futures) {
                    final var items = future.getNow(null).getItems();
                    if (items != null && !items.isEmpty()) {
                        items.stream()
                                .map(item -> item.getItemData())
                                // Just return item names 
                                .map(l -> l.getName())
                                .forEach(name -> {
                                    itemNames.add(name);
                                });
                    }
                }

                if (itemNames.isEmpty()) {
                    return mapper.createObjectNode().put("message", "No items match the search query");
                } else {
                    // return only 5 items (and remove dups of course)
                    return itemNames.stream().distinct().limit(5).toList();
                }

            } catch (CompletionException e) {
                log.error("Async Completion Error", e.getCause());
                return mapper.createObjectNode().put("status", "FAILED").put("error_message", e.getLocalizedMessage());
            } catch (Exception ex) {
                log.error("Unhandled Error", ex);
                return mapper.createObjectNode().put("status", "FAILED").put("error_message", ex.getLocalizedMessage());
            }
        };
    }

    private static class Request {

        @JsonPropertyDescription("the search text to search for items for sale in English language")
        @JsonProperty(required = true)
        public String search_text;
    }

    @Override
    protected boolean isEnabled() {
        return isSquareEnabled();
    }

}
