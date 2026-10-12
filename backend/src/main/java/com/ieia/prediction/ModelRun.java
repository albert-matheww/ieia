package com.ieia.prediction;

import com.ieia.common.BaseIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** One ML training/evaluation execution — for reproducible, cite-able results. */
@Entity
@Table(name = "model_runs")
public class ModelRun extends BaseIdEntity {

    @Column(name = "model_type", nullable = false)
    private String modelType;          // PREDICTION | ANOMALY

    @Column(name = "target_metric")
    private String targetMetric;

    @Column(nullable = false)
    private String algorithm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> params = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> metrics = new LinkedHashMap<>();

    @Column(name = "training_rows", nullable = false)
    private int trainingRows;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @CreationTimestamp
    @Column(name = "trained_at", nullable = false, updatable = false)
    private Instant trainedAt;

    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public String getTargetMetric() { return targetMetric; }
    public void setTargetMetric(String targetMetric) { this.targetMetric = targetMetric; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    public int getTrainingRows() { return trainingRows; }
    public void setTrainingRows(int trainingRows) { this.trainingRows = trainingRows; }
    public int getDurationMs() { return durationMs; }
    public void setDurationMs(int durationMs) { this.durationMs = durationMs; }
    public Instant getTrainedAt() { return trainedAt; }
}
