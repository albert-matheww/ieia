package com.ieia.scoring;

import com.ieia.common.BaseIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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

/** The transparent 0–100 environmental score for one user-month (docs/06). */
@Entity
@Table(name = "environmental_scores")
public class EnvironmentalScore extends BaseIdEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(name = "scoring_config_id", nullable = false)
    private UUID scoringConfigId;

    @Column(name = "overall_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "carbon_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal carbonScore;

    @Column(name = "energy_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal energyScore;

    @Column(name = "water_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal waterScore;

    @Column(name = "waste_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal wasteScore;

    @Column(name = "transport_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal transportScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown", nullable = false)
    private Map<String, Object> scoreBreakdown = new LinkedHashMap<>();

    @Column(name = "previous_score", precision = 6, scale = 2)
    private BigDecimal previousScore;

    @Column(precision = 6, scale = 2)
    private BigDecimal delta;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Object>> drivers = new ArrayList<>();

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(LocalDate periodMonth) { this.periodMonth = periodMonth; }
    public UUID getScoringConfigId() { return scoringConfigId; }
    public void setScoringConfigId(UUID scoringConfigId) { this.scoringConfigId = scoringConfigId; }
    public BigDecimal getOverallScore() { return overallScore; }
    public void setOverallScore(BigDecimal v) { this.overallScore = v; }
    public BigDecimal getCarbonScore() { return carbonScore; }
    public void setCarbonScore(BigDecimal v) { this.carbonScore = v; }
    public BigDecimal getEnergyScore() { return energyScore; }
    public void setEnergyScore(BigDecimal v) { this.energyScore = v; }
    public BigDecimal getWaterScore() { return waterScore; }
    public void setWaterScore(BigDecimal v) { this.waterScore = v; }
    public BigDecimal getWasteScore() { return wasteScore; }
    public void setWasteScore(BigDecimal v) { this.wasteScore = v; }
    public BigDecimal getTransportScore() { return transportScore; }
    public void setTransportScore(BigDecimal v) { this.transportScore = v; }
    public Map<String, Object> getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(Map<String, Object> v) { this.scoreBreakdown = v; }
    public BigDecimal getPreviousScore() { return previousScore; }
    public void setPreviousScore(BigDecimal v) { this.previousScore = v; }
    public BigDecimal getDelta() { return delta; }
    public void setDelta(BigDecimal delta) { this.delta = delta; }
    public List<Map<String, Object>> getDrivers() { return drivers; }
    public void setDrivers(List<Map<String, Object>> drivers) { this.drivers = drivers; }
    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }
}
