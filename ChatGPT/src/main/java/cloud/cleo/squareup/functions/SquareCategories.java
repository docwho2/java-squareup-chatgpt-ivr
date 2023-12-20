/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.functions.AbstractFunction.allCombinations;
import static cloud.cleo.squareup.functions.AbstractFunction.getSquareClient;
import static cloud.cleo.squareup.functions.AbstractFunction.log;
import static cloud.cleo.squareup.functions.AbstractFunction.mapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.squareup.square.models.CatalogQuery;
import com.squareup.square.models.CatalogQueryText;
import com.squareup.square.models.SearchCatalogObjectsRequest;
import com.squareup.square.models.SearchCatalogObjectsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

                // Generate a list of search tokens with every combination of the words
                final List<String> tokens = allCombinations(r.search_text);

                // List to hold all the search futures
                final List<CompletableFuture<SearchCatalogObjectsResponse>> futures = new ArrayList<>(tokens.size());

                // Launch all the searches
                for (final var token : tokens) {
                    futures.add(getSquareClient().getCatalogApi()
                            // Only retrieve Category objects
                            .searchCatalogObjectsAsync(new SearchCatalogObjectsRequest.Builder()
                                    .includeDeletedObjects(false)
                                    .objectTypes(List.of("CATEGORY"))
                                    .query(new CatalogQuery.Builder().textQuery(new CatalogQueryText(List.of(r.search_text))).build())
                                    .build()));
                    log.debug("Launching Category Search on [{}]", token);
                }

                log.debug("Starting wait on {} futures", futures.size());
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                log.debug("All Futures completed, processing results");

                // Category names to return to GPT
                final List<String> catNames = new ArrayList<>();

                // Process the results
                for (final var future : futures) {
                    final var cats = future.getNow(null).getObjects();
                    if (cats != null && !cats.isEmpty()) {
                        cats.stream()
                                .map(item -> item.getCategoryData().getName())
                                .forEach(name -> {
                                    catNames.add(name);
                                });
                    }
                }

                if (!catNames.isEmpty()) {
                    return catNames.stream().distinct().limit(5).toList();
                } else {
                    return mapper.createObjectNode().put("message", "No categories match the search query");
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

        @JsonPropertyDescription("the search text to search for item categories in English Language")
        @JsonProperty(required = true)
        public String search_text;
    }

    @Override
    protected boolean isEnabled() {
        return isSquareEnabled();
    }

}
