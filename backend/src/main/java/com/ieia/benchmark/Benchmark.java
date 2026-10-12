package com.ieia.benchmark;

import com.ieia.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A configurable comparison value (docs/03-api-specification.md §13). These are
 * <b>not</b> official standards ({@code isOfficial} defaults false).
 */
@Entity
@Table(name = "benchmarks")
public class Benchmark extends BaseEntity {

    @Column(nullable = false)
    private String scope;          // INDIVIDUAL | ORGANIZATION

    @Column(nullable = false)
    private String segment;        // e.g. IN_URBAN_HOUSEHOLD, OFFICE_PER_EMPLOYEE

    @Column(nullable = false)
    private String metric;         // CO2E | ENERGY | WATER | WASTE | SCORE

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false)
    private String period;         // MONTH | YEAR

    @Column(nullable = false, precision = 16, scale = 4)
    private BigDecimal value;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rating_bands", nullable = false)
    private Map<String, Object> ratingBands = new LinkedHashMap<>();

    @Column(nullable = false)
    private String region = "GLOBAL";

    @Column(nullable = false)
    private String source;

    @Column(name = "is_official", nullable = false)
    private boolean official = false;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.of(2000, 1, 1);

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getSegment() { return segment; }
    public void setSegment(String segment) { this.segment = segment; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public Map<String, Object> getRatingBands() { return ratingBands; }
    public void setRatingBands(Map<String, Object> ratingBands) { this.ratingBands = ratingBands; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isOfficial() { return official; }
    public void setOfficial(boolean official) { this.official = official; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
