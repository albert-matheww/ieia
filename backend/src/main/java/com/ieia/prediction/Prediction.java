package com.ieia.prediction;

import com.ieia.common.BaseIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A persisted ML forecast for one (user, target metric, horizon month). */
@Entity
@Table(name = "predictions")
public class Prediction extends BaseIdEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "model_run_id")
    private UUID modelRunId;

    @Column(name = "target_metric", nullable = false)
    private String targetMetric;

    @Column(name = "generated_for_month", nullable = false)
    private LocalDate generatedForMonth;

    @Column(name = "horizon_month", nullable = false)
    private LocalDate horizonMonth;

    @Column(name = "current_value", nullable = false, precision = 16, scale = 4)
    private BigDecimal currentValue;

    @Column(name = "predicted_value", nullable = false, precision = 16, scale = 4)
    private BigDecimal predictedValue;

    @Column(name = "lower_bound", precision = 16, scale = 4)
    private BigDecimal lowerBound;

    @Column(name = "upper_bound", precision = 16, scale = 4)
    private BigDecimal upperBound;

    @Column(name = "expected_change_abs", precision = 16, scale = 4)
    private BigDecimal expectedChangeAbs;

    @Column(name = "expected_change_pct", precision = 8, scale = 2)
    private BigDecimal expectedChangePct;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_importance", nullable = false)
    private List<Map<String, Object>> featureImportance = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> metrics = new LinkedHashMap<>();

    @Column(name = "confidence_note", columnDefinition = "text")
    private String confidenceNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getModelRunId() { return modelRunId; }
    public void setModelRunId(UUID modelRunId) { this.modelRunId = modelRunId; }
    public String getTargetMetric() { return targetMetric; }
    public void setTargetMetric(String targetMetric) { this.targetMetric = targetMetric; }
    public LocalDate getGeneratedForMonth() { return generatedForMonth; }
    public void setGeneratedForMonth(LocalDate v) { this.generatedForMonth = v; }
    public LocalDate getHorizonMonth() { return horizonMonth; }
    public void setHorizonMonth(LocalDate v) { this.horizonMonth = v; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal v) { this.currentValue = v; }
    public BigDecimal getPredictedValue() { return predictedValue; }
    public void setPredictedValue(BigDecimal v) { this.predictedValue = v; }
    public BigDecimal getLowerBound() { return lowerBound; }
    public void setLowerBound(BigDecimal v) { this.lowerBound = v; }
    public BigDecimal getUpperBound() { return upperBound; }
    public void setUpperBound(BigDecimal v) { this.upperBound = v; }
    public BigDecimal getExpectedChangeAbs() { return expectedChangeAbs; }
    public void setExpectedChangeAbs(BigDecimal v) { this.expectedChangeAbs = v; }
    public BigDecimal getExpectedChangePct() { return expectedChangePct; }
    public void setExpectedChangePct(BigDecimal v) { this.expectedChangePct = v; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public List<Map<String, Object>> getFeatureImportance() { return featureImportance; }
    public void setFeatureImportance(List<Map<String, Object>> v) { this.featureImportance = v; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    public String getConfidenceNote() { return confidenceNote; }
    public void setConfidenceNote(String confidenceNote) { this.confidenceNote = confidenceNote; }
    public Instant getCreatedAt() { return createdAt; }
}
