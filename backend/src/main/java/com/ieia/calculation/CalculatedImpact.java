package com.ieia.calculation;

import com.ieia.emissionfactor.ImpactType;
import com.ieia.record.EnvironmentalRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Derived emission for one {@link EnvironmentalRecord} in one {@link ImpactType}.
 * Never user-editable. The factor value used is snapshotted so historical
 * numbers do not change when a factor is later updated
 * (docs/02-database-schema.md §3).
 */
@Entity
@Table(name = "calculated_impacts")
public class CalculatedImpact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environmental_record_id", nullable = false)
    private EnvironmentalRecord record;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_type", nullable = false)
    private ImpactType impactType;

    @Column(nullable = false, precision = 16, scale = 4)
    private BigDecimal value;

    @Column(nullable = false)
    private String unit;

    @Column(name = "emission_factor_id")
    private UUID emissionFactorId;

    @Column(name = "factor_value", precision = 16, scale = 6)
    private BigDecimal factorValue;

    @Column(name = "calculation_method", nullable = false)
    private String calculationMethod = "amount * factor";

    @CreationTimestamp
    @Column(name = "calculated_at", nullable = false, updatable = false)
    private Instant calculatedAt;

    public UUID getId() { return id; }
    public EnvironmentalRecord getRecord() { return record; }
    public void setRecord(EnvironmentalRecord record) { this.record = record; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(LocalDate periodMonth) { this.periodMonth = periodMonth; }
    public ImpactType getImpactType() { return impactType; }
    public void setImpactType(ImpactType impactType) { this.impactType = impactType; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public UUID getEmissionFactorId() { return emissionFactorId; }
    public void setEmissionFactorId(UUID emissionFactorId) { this.emissionFactorId = emissionFactorId; }
    public BigDecimal getFactorValue() { return factorValue; }
    public void setFactorValue(BigDecimal factorValue) { this.factorValue = factorValue; }
    public String getCalculationMethod() { return calculationMethod; }
    public void setCalculationMethod(String calculationMethod) { this.calculationMethod = calculationMethod; }
    public Instant getCalculatedAt() { return calculatedAt; }
}
