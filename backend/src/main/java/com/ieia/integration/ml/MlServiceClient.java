package com.ieia.integration.ml;

import com.ieia.integration.ml.dto.MlAnalyticsDtos;
import com.ieia.integration.ml.dto.MlAnomalyRequest;
import com.ieia.integration.ml.dto.MlAnomalyResponse;
import com.ieia.integration.ml.dto.MlPredictRequest;
import com.ieia.integration.ml.dto.MlPredictResponse;

/**
 * Abstraction over the stateless FastAPI ML tier (docs/03-api-specification.md §16).
 * Implementations must translate transport failures into
 * {@link com.ieia.common.exception.ApiException} with
 * {@code ErrorCode.ML_SERVICE_UNAVAILABLE} — the backend never fabricates a
 * result when the ML service is down.
 */
public interface MlServiceClient {

    MlPredictResponse predict(MlPredictRequest request);

    MlAnomalyResponse detectAnomalies(MlAnomalyRequest request);

    MlAnalyticsDtos.ContributorsResponse contributors(MlAnalyticsDtos.ContributorsRequest request);

    MlAnalyticsDtos.ExplainResponse explain(MlAnalyticsDtos.ExplainRequest request);

    boolean isHealthy();
}
