package com.ieia.anomaly;

import com.ieia.common.BaseIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A detected unusual spike/drop in one metric for one month (docs/05 §3). */
@Entity
@Table(name = "anomalies")
public class Anomaly extends BaseIdEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "environmental_record_id")
    private UUID environmentalRecordId;

    @Column(name = "activity_type_id")
    private UUID activityTypeId;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(nullable = false)
    private String metric;

    @Column(nullable = false)
    private String method;

    @Column(name = "observed_value", nullable = false, precision = 16, scale = 4)
    private BigDecimal observedValue;

    @Column(name = "baseline_value", nullable = false, precision = 16, scale = 4)
    private BigDecimal baselineValue;

    @Column(name = "deviation_abs", nullable = false, precision = 16, scale = 4)
    private BigDecimal deviationAbs;

    @Column(name = "deviation_pct", nullable = false, precision = 8, scale = 2)
    private BigDecimal deviationPct;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal score;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String direction;

    @Column(name = "estimated_co2e_impact_kg", precision = 16, scale = 4)
    private BigDecimal estimatedCo2eImpactKg;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getEnvironmentalRecordId() { return environmentalRecordId; }
    public void setEnvironmentalRecordId(UUID v) { this.environmentalRecordId = v; }
    public UUID getActivityTypeId() { return activityTypeId; }
    public void setActivityTypeId(UUID v) { this.activityTypeId = v; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(LocalDate v) { this.periodMonth = v; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public BigDecimal getObservedValue() { return observedValue; }
    public void setObservedValue(BigDecimal v) { this.observedValue = v; }
    public BigDecimal getBaselineValue() { return baselineValue; }
    public void setBaselineValue(BigDecimal v) { this.baselineValue = v; }
    public BigDecimal getDeviationAbs() { return deviationAbs; }
    public void setDeviationAbs(BigDecimal v) { this.deviationAbs = v; }
    public BigDecimal getDeviationPct() { return deviationPct; }
    public void setDeviationPct(BigDecimal v) { this.deviationPct = v; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public BigDecimal getEstimatedCo2eImpactKg() { return estimatedCo2eImpactKg; }
    public void setEstimatedCo2eImpactKg(BigDecimal v) { this.estimatedCo2eImpactKg = v; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public Instant getDetectedAt() { return detectedAt; }
}
