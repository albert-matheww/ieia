package com.ieia.recommendation;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A ranked, quantified reduction action for one user-month (docs/03 §11). */
@Entity
@Table(name = "recommendations")
public class Recommendation extends BaseIdEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(nullable = false)
    private int rank;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String rationale;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assumed_change", nullable = false)
    private Map<String, Object> assumedChange = new LinkedHashMap<>();

    @Column(name = "estimated_reduction_co2e_kg", nullable = false, precision = 16, scale = 4)
    private BigDecimal estimatedReductionCo2eKg = BigDecimal.ZERO;

    @Column(name = "estimated_reduction_pct", nullable = false, precision = 8, scale = 2)
    private BigDecimal estimatedReductionPct = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estimated_other", nullable = false)
    private Map<String, Object> estimatedOther = new LinkedHashMap<>();

    @Column(nullable = false)
    private String confidence = "MEDIUM";

    @Column(nullable = false)
    private String basis = "DATA_DRIVEN";

    @Column(nullable = false)
    private String source = "RULE_ENGINE";

    @Column(nullable = false)
    private String status = "SUGGESTED";

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(LocalDate v) { this.periodMonth = v; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public Map<String, Object> getAssumedChange() { return assumedChange; }
    public void setAssumedChange(Map<String, Object> v) { this.assumedChange = v; }
    public BigDecimal getEstimatedReductionCo2eKg() { return estimatedReductionCo2eKg; }
    public void setEstimatedReductionCo2eKg(BigDecimal v) { this.estimatedReductionCo2eKg = v; }
    public BigDecimal getEstimatedReductionPct() { return estimatedReductionPct; }
    public void setEstimatedReductionPct(BigDecimal v) { this.estimatedReductionPct = v; }
    public Map<String, Object> getEstimatedOther() { return estimatedOther; }
    public void setEstimatedOther(Map<String, Object> v) { this.estimatedOther = v; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public String getBasis() { return basis; }
    public void setBasis(String basis) { this.basis = basis; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getGeneratedAt() { return generatedAt; }
}
