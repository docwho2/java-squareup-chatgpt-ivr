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
                final List<String> tokens = reduceTokens(r.search_text);

                final List<CompletableFuture<SearchCatalogItemsResponse>> futures = new ArrayList<>(tokens.size());

                for (final var token : tokens) {
                    futures.add(getSquareClient().getCatalogApi()
                            .searchCatalogItemsAsync(new SearchCatalogItemsRequest.Builder().textFilter(token).limit(5).build()));
                    log.debug("Queing Search on [" + token + "]");
                }

                log.debug("Starting wait on " + futures.size() + " futures");
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                log.debug("All Futures completed, processing results");

                // Item names to return to GPT
                final List<String> itemNames = new ArrayList<>(5);

                // Process the results
                for (var future : futures) {
                    final var items = future.getNow(null).getItems();
                    if (items != null && !items.isEmpty()) {
                        items.stream()
                                .map(item -> item.getItemData())
                                // Just return item names 
                                .map(l -> l.getName())
                                .forEach(name -> {
                                    if (itemNames.size() < 5) {
                                        itemNames.add(name);
                                    }
                                });
                    }
                    // If we have 5 already don't need to process any more futures
                    if (itemNames.size() >= 5) {
                        break;
                    }
                }

                if (itemNames.isEmpty()) {
                    return mapper.createObjectNode().put("message", "No items match the search query");
                } else {
                    return itemNames;
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

    public static List<String> reduceTokens(String input) {
        List<String> reducedStrings = new ArrayList<>();
        String[] tokens = input.split(" ");

        for (int i = tokens.length; i > 0; i--) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < i; j++) {
                sb.append(tokens[j]);
                if (j < i - 1) {
                    sb.append(" "); // Add space between words
                }
            }
            reducedStrings.add(sb.toString());
        }

        return reducedStrings;
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
