package com.ieia.scenario;

import com.ieia.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A saved what-if scenario (docs/02-database-schema.md §3). */
@Entity
@Table(name = "scenarios")
public class Scenario extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "base_period_from", nullable = false)
    private LocalDate basePeriodFrom;

    @Column(name = "base_period_to", nullable = false)
    private LocalDate basePeriodTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "baseline_snapshot", nullable = false)
    private Map<String, Object> baselineSnapshot = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Object>> adjustments = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> result = new LinkedHashMap<>();

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getBasePeriodFrom() { return basePeriodFrom; }
    public void setBasePeriodFrom(LocalDate v) { this.basePeriodFrom = v; }
    public LocalDate getBasePeriodTo() { return basePeriodTo; }
    public void setBasePeriodTo(LocalDate v) { this.basePeriodTo = v; }
    public Map<String, Object> getBaselineSnapshot() { return baselineSnapshot; }
    public void setBaselineSnapshot(Map<String, Object> v) { this.baselineSnapshot = v; }
    public List<Map<String, Object>> getAdjustments() { return adjustments; }
    public void setAdjustments(List<Map<String, Object>> v) { this.adjustments = v; }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> v) { this.result = v; }
}
