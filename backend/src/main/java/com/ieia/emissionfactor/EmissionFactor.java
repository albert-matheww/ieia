package com.ieia.emissionfactor;

import com.ieia.activity.ActivityType;
import com.ieia.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Configurable conversion factor: {@code impact = amount(unit_from) * value → unit_to}.
 * Region-aware and time-effective. Resolution logic lives in {@link FactorResolver}.
 */
@Entity
@Table(name = "emission_factors")
public class EmissionFactor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_type_id", nullable = false)
    private ActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_type", nullable = false)
    private ImpactType impactType;

    @Column(name = "unit_from", nullable = false)
    private String unitFrom;

    @Column(name = "unit_to", nullable = false)
    private String unitTo;

    @Column(nullable = false, precision = 16, scale = 6)
    private BigDecimal value;

    @Column(nullable = false)
    private String region = "GLOBAL";

    @Column(nullable = false)
    private String source;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.of(2000, 1, 1);

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public boolean coversPeriod(LocalDate period) {
        boolean fromOk = !period.isBefore(effectiveFrom);
        boolean toOk = effectiveTo == null || period.isBefore(effectiveTo);
        return fromOk && toOk;
    }

    public ActivityType getActivityType() { return activityType; }
    public void setActivityType(ActivityType activityType) { this.activityType = activityType; }
    public ImpactType getImpactType() { return impactType; }
    public void setImpactType(ImpactType impactType) { this.impactType = impactType; }
    public String getUnitFrom() { return unitFrom; }
    public void setUnitFrom(String unitFrom) { this.unitFrom = unitFrom; }
    public String getUnitTo() { return unitTo; }
    public void setUnitTo(String unitTo) { this.unitTo = unitTo; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
