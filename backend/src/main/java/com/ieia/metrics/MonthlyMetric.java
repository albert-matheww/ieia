package com.ieia.metrics;

import com.ieia.common.BaseIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregated rollup of one user's impacts for one month
 * (docs/02-database-schema.md §3). Derived — rebuilt by
 * {@link MetricsAggregationService} whenever that month's records change.
 */
@Entity
@Table(name = "monthly_metrics")
public class MonthlyMetric extends BaseIdEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(name = "total_co2e_kg", nullable = false, precision = 16, scale = 4)
    private BigDecimal totalCo2eKg = BigDecimal.ZERO;

    @Column(name = "total_energy_mj", nullable = false, precision = 16, scale = 4)
    private BigDecimal totalEnergyMj = BigDecimal.ZERO;

    @Column(name = "total_water_litre", nullable = false, precision = 16, scale = 4)
    private BigDecimal totalWaterLitre = BigDecimal.ZERO;

    @Column(name = "total_waste_kg", nullable = false, precision = 16, scale = 4)
    private BigDecimal totalWasteKg = BigDecimal.ZERO;

    @Column(name = "recycled_fraction", nullable = false, precision = 6, scale = 4)
    private BigDecimal recycledFraction = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "co2e_by_category", nullable = false)
    private Map<String, BigDecimal> co2eByCategory = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "co2e_by_activity", nullable = false)
    private Map<String, BigDecimal> co2eByActivity = new LinkedHashMap<>();

    @Column(name = "record_count", nullable = false)
    private int recordCount = 0;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(LocalDate periodMonth) { this.periodMonth = periodMonth; }
    public BigDecimal getTotalCo2eKg() { return totalCo2eKg; }
    public void setTotalCo2eKg(BigDecimal v) { this.totalCo2eKg = v; }
    public BigDecimal getTotalEnergyMj() { return totalEnergyMj; }
    public void setTotalEnergyMj(BigDecimal v) { this.totalEnergyMj = v; }
    public BigDecimal getTotalWaterLitre() { return totalWaterLitre; }
    public void setTotalWaterLitre(BigDecimal v) { this.totalWaterLitre = v; }
    public BigDecimal getTotalWasteKg() { return totalWasteKg; }
    public void setTotalWasteKg(BigDecimal v) { this.totalWasteKg = v; }
    public BigDecimal getRecycledFraction() { return recycledFraction; }
    public void setRecycledFraction(BigDecimal v) { this.recycledFraction = v; }
    public Map<String, BigDecimal> getCo2eByCategory() { return co2eByCategory; }
    public void setCo2eByCategory(Map<String, BigDecimal> v) { this.co2eByCategory = v; }
    public Map<String, BigDecimal> getCo2eByActivity() { return co2eByActivity; }
    public void setCo2eByActivity(Map<String, BigDecimal> v) { this.co2eByActivity = v; }
    public int getRecordCount() { return recordCount; }
    public void setRecordCount(int recordCount) { this.recordCount = recordCount; }
    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }
}
