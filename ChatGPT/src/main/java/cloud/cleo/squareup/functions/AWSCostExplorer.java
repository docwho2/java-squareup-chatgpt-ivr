package cloud.cleo.squareup.functions;

import static cloud.cleo.squareup.ChatGPTLambda.crtAsyncHttpClient;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.LocalDate;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import software.amazon.awssdk.services.costexplorer.CostExplorerAsyncClient;
import software.amazon.awssdk.services.costexplorer.model.GetCostForecastRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostForecastResponse;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.Metric;

/**
 * Send an Email message
 *
 * @author sjensen
 * @param <Request>
 */
public class AWSCostExplorer<Request> extends AbstractFunction {

    protected final static CostExplorerAsyncClient costExplorerAsyncClient = CostExplorerAsyncClient.builder()
            .httpClient(crtAsyncHttpClient)
            .build();

    @Override
    public String getName() {
        return "aws_cost_forecast";
    }

    @Override
    public String getDescription() {
        return "Retrieves a forecast for how much Amazon Web Services (AWS) predicts that you will spend over the forecast time period that you select, based on your past costs.";
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

                final var cfr = GetCostForecastRequest.builder()
                        .timePeriod(b -> b.start(r.start_date.toString()).end(r.end_date.plusDays(1).toString()))
                        .granularity(r.granularity != null ? r.granularity : Granularity.MONTHLY)
                        .metric(Metric.BLENDED_COST)
                        .build();

                GetCostForecastResponse res =  costExplorerAsyncClient.getCostForecast(cfr).join();
                final var json = mapper.valueToTree(mapper.convertValue(res.toBuilder(), GetCostForecastResponse.serializableBuilderClass()));
                log.debug("Cost Forecast response is " + json.toPrettyString());
                return json;
            } catch (CompletionException e) {
                log.error("Unhandled Error", e.getCause());
                return mapper.createObjectNode().put("status", "FAILED").put("message", "An error has occurred, the cost could not be determined.");
            } catch (Exception e) {
                log.error("Unhandled Error", e);
                return mapper.createObjectNode().put("status", "FAILED").put("message", "An error has occurred, the cost could not be determined.");
            }
        };
    }

    private static class Request {

        @JsonPropertyDescription("Start date in ISO format (YYYY-MM-DD).  The start date must be equal to or no later than the current date to avoid a validation error")
        @JsonProperty(required = true)
        public LocalDate start_date;

        @JsonPropertyDescription("End date in ISO format (YYYY-MM-DD).")
        @JsonProperty(required = true)
        public LocalDate end_date;

        @JsonPropertyDescription("How granular you want the forecast to be. You can get 3 months of DAILY forecasts or 12 months of MONTHLY forecasts. Defaults to MONTHLY.")
        @JsonProperty(required = false)
        public Granularity granularity;
    }


}
