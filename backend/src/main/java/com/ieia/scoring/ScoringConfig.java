package com.ieia.scoring;

import com.ieia.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A versioned, configurable scoring methodology. Exactly one row is active
 * (enforced by a partial unique index). See docs/06-scoring-methodology.md.
 *
 * <ul>
 *   <li>{@code weights}      — {@code {carbon,energy,water,waste,transport}} summing to 1</li>
 *   <li>{@code baselines}    — {@code baselines[scope][region][METRIC] = {good, poor, unit}}</li>
 *   <li>{@code scaleParams}  — {@code {clamp:[0,100], recycling_bonus_max, round_dp, insufficient_data_months}}</li>
 * </ul>
 */
@Entity
@Table(name = "scoring_configs")
public class ScoringConfig extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String version;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Double> weights = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> baselines = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scale_params", nullable = false)
    private Map<String, Object> scaleParams = new LinkedHashMap<>();

    @Column(columnDefinition = "text")
    private String description;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Map<String, Double> getWeights() { return weights; }
    public void setWeights(Map<String, Double> weights) { this.weights = weights; }
    public Map<String, Object> getBaselines() { return baselines; }
    public void setBaselines(Map<String, Object> baselines) { this.baselines = baselines; }
    public Map<String, Object> getScaleParams() { return scaleParams; }
    public void setScaleParams(Map<String, Object> scaleParams) { this.scaleParams = scaleParams; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
