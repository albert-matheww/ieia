package com.ieia.integration.ml;

import com.ieia.common.exception.ApiException;
import com.ieia.config.AppProperties;
import com.ieia.integration.ml.dto.MlAnalyticsDtos;
import com.ieia.integration.ml.dto.MlAnomalyRequest;
import com.ieia.integration.ml.dto.MlAnomalyResponse;
import com.ieia.integration.ml.dto.MlPredictRequest;
import com.ieia.integration.ml.dto.MlPredictResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * {@link MlServiceClient} over HTTP using Spring's synchronous {@link RestClient}.
 * One quick retry on a transient transport error; any other failure surfaces as
 * {@code 503 ML_SERVICE_UNAVAILABLE}.
 */
@Component
public class RestMlServiceClient implements MlServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RestMlServiceClient.class);

    private final RestClient http;

    public RestMlServiceClient(AppProperties props) {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        int timeout = props.getMl().getTimeoutMs();
        requestFactory.setConnectTimeout((int) Duration.ofMillis(Math.min(timeout, 4000)).toMillis());
        requestFactory.setReadTimeout(timeout);
        this.http = RestClient.builder()
                .baseUrl(props.getMl().getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getMl().getToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public MlPredictResponse predict(MlPredictRequest request) {
        return call("/predict", request, MlPredictResponse.class);
    }

    @Override
    public MlAnomalyResponse detectAnomalies(MlAnomalyRequest request) {
        return call("/anomalies", request, MlAnomalyResponse.class);
    }

    @Override
    public MlAnalyticsDtos.ContributorsResponse contributors(MlAnalyticsDtos.ContributorsRequest request) {
        return call("/analytics/contributors", request, MlAnalyticsDtos.ContributorsResponse.class);
    }

    @Override
    public MlAnalyticsDtos.ExplainResponse explain(MlAnalyticsDtos.ExplainRequest request) {
        return call("/analytics/explain", request, MlAnalyticsDtos.ExplainResponse.class);
    }

    @Override
    public boolean isHealthy() {
        try {
            http.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private <T> T call(String path, Object body, Class<T> responseType) {
        try {
            return doCall(path, body, responseType);
        } catch (ResourceAccessException transientError) {
            log.warn("ML call {} failed ({}), retrying once", path, transientError.getMessage());
            try {
                return doCall(path, body, responseType);
            } catch (RuntimeException retryError) {
                throw ApiException.mlUnavailable("ML service is unavailable", retryError);
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ApiException.mlUnavailable("ML service returned an error", e);
        }
    }

    private <T> T doCall(String path, Object body, Class<T> responseType) {
        return http.post()
                .uri(path)
                .body(body)
                .retrieve()
                .onStatus(status -> status.value() == 422, (req, res) -> {
                    throw ApiException.unprocessable("ML service rejected the request: HTTP 422");
                })
                .body(responseType);
    }
}
