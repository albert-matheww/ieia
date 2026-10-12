package com.ieia.calculation;

import com.ieia.emissionfactor.EmissionFactor;
import com.ieia.emissionfactor.EmissionFactorRepository;
import com.ieia.emissionfactor.FactorResolver;
import com.ieia.emissionfactor.ImpactType;
import com.ieia.record.EnvironmentalRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one {@link EnvironmentalRecord} into its {@link CalculatedImpact} rows —
 * one per impact dimension for which an {@link EmissionFactor} exists.
 *
 * <pre>
 *   impact_value = convert(record.amount, record.unit → factor.unit_from) × factor.value
 * </pre>
 *
 * The factor's numeric value is snapshotted onto the impact row so the result
 * is reproducible after future factor edits (docs/02-database-schema.md §3).
 * Pure function of its inputs — no persistence here.
 */
@Component
public class ImpactCalculationEngine {

    private static final Logger log = LoggerFactory.getLogger(ImpactCalculationEngine.class);
    private static final int IMPACT_SCALE = 4;

    private final EmissionFactorRepository factorRepository;
    private final FactorResolver factorResolver;
    private final UnitConverter unitConverter;

    public ImpactCalculationEngine(EmissionFactorRepository factorRepository,
                                   FactorResolver factorResolver,
                                   UnitConverter unitConverter) {
        this.factorRepository = factorRepository;
        this.factorResolver = factorResolver;
        this.unitConverter = unitConverter;
    }

    public record CalculationResult(List<CalculatedImpact> impacts, List<String> warnings) {}

    public CalculationResult calculate(EnvironmentalRecord record) {
        String region = resolveRegion(record);
        LocalDate period = record.getPeriodMonth();

        // Which impact dimensions can this activity produce at all?
        List<ImpactType> dimensions = factorRepository
                .findActiveByActivityType(record.getActivityType().getId())
                .stream().map(EmissionFactor::getImpactType).distinct().toList();

        List<CalculatedImpact> impacts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (ImpactType dimension : dimensions) {
            var factorOpt = factorResolver.resolve(record.getActivityType(), dimension, region, period);
            if (factorOpt.isEmpty()) {
                warnings.add("NO_FACTOR:" + dimension);
                continue;
            }
            EmissionFactor factor = factorOpt.get();

            BigDecimal converted = unitConverter.convert(record.getAmount(), record.getUnit(), factor.getUnitFrom());
            BigDecimal value = converted.multiply(factor.getValue(), MathContext.DECIMAL64)
                    .setScale(IMPACT_SCALE, RoundingMode.HALF_UP);

            CalculatedImpact impact = new CalculatedImpact();
            impact.setRecord(record);
            impact.setUserId(record.getUser().getId());
            impact.setPeriodMonth(period);
            impact.setImpactType(dimension);
            impact.setValue(value);
            impact.setUnit(factor.getUnitTo());
            impact.setEmissionFactorId(factor.getId());
            impact.setFactorValue(factor.getValue());
            impact.setCalculationMethod(String.format(
                    "%s %s × %s %s/%s",
                    record.getAmount().stripTrailingZeros().toPlainString(), record.getUnit(),
                    factor.getValue().stripTrailingZeros().toPlainString(),
                    factor.getUnitTo(), factor.getUnitFrom()));
            impacts.add(impact);
        }

        if (impacts.isEmpty()) {
            log.warn("Record {} ({}) produced no impacts — no emission factors for region/period",
                    record.getId(), record.getActivityType().getCode());
        }
        return new CalculationResult(impacts, warnings);
    }

    /** Sum a record's impacts by dimension (used by scenario/recommendation maths). */
    public static Map<ImpactType, BigDecimal> totals(List<CalculatedImpact> impacts) {
        Map<ImpactType, BigDecimal> out = new LinkedHashMap<>();
        for (CalculatedImpact ci : impacts) {
            out.merge(ci.getImpactType(), ci.getValue(), BigDecimal::add);
        }
        return out;
    }

    private String resolveRegion(EnvironmentalRecord record) {
        if (record.getOrganization() != null && record.getOrganization().getRegion() != null) {
            return record.getOrganization().getRegion();
        }
        if (record.getUser() != null && record.getUser().getRegion() != null) {
            return record.getUser().getRegion();
        }
        return "GLOBAL";
    }
}
