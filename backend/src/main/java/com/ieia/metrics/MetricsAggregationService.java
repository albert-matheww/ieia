package com.ieia.metrics;

import com.ieia.activity.ActivityCategory;
import com.ieia.calculation.CalculatedImpact;
import com.ieia.calculation.CalculatedImpactRepository;
import com.ieia.calculation.UnitConverter;
import com.ieia.emissionfactor.ImpactType;
import com.ieia.record.EnvironmentalRecord;
import com.ieia.user.User;
import com.ieia.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rebuilds {@link MonthlyMetric} for one {@code (user, month)} from that month's
 * {@link CalculatedImpact} rows. Idempotent — safe to call repeatedly.
 * See docs/02-database-schema.md §3 and docs/06-scoring-methodology.md §1.
 */
@Service
public class MetricsAggregationService {

    private static final Logger log = LoggerFactory.getLogger(MetricsAggregationService.class);
    private static final Set<String> DIVERTING_ACTIVITIES = Set.of("waste_recycled", "waste_organic_compost");
    private static final int SCALE = 4;

    private final CalculatedImpactRepository impactRepository;
    private final MonthlyMetricRepository metricRepository;
    private final UserRepository userRepository;
    private final UnitConverter unitConverter;

    public MetricsAggregationService(CalculatedImpactRepository impactRepository,
                                     MonthlyMetricRepository metricRepository,
                                     UserRepository userRepository,
                                     UnitConverter unitConverter) {
        this.impactRepository = impactRepository;
        this.metricRepository = metricRepository;
        this.userRepository = userRepository;
        this.unitConverter = unitConverter;
    }

    @Transactional
    public MonthlyMetric recomputeMonth(UUID userId, LocalDate period) {
        List<CalculatedImpact> impacts = impactRepository.findDetailedForUserMonth(userId, period);

        if (impacts.isEmpty()) {
            metricRepository.findByUserIdAndPeriodMonth(userId, period)
                    .ifPresent(metricRepository::delete);
            log.debug("No impacts for user {} {} — monthly metric cleared", userId, period);
            return null;
        }

        MonthlyMetric metric = metricRepository.findByUserIdAndPeriodMonth(userId, period)
                .orElseGet(() -> {
                    MonthlyMetric m = new MonthlyMetric();
                    m.setUserId(userId);
                    m.setPeriodMonth(period);
                    return m;
                });

        User user = userRepository.findByIdWithOrganization(userId).orElse(null);
        metric.setOrganizationId(user != null && user.getOrganization() != null
                ? user.getOrganization().getId() : null);

        BigDecimal totalCo2e = BigDecimal.ZERO;
        BigDecimal totalEnergy = BigDecimal.ZERO;
        BigDecimal totalWater = BigDecimal.ZERO;
        BigDecimal totalWaste = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> byActivity = new LinkedHashMap<>();
        java.util.Set<UUID> recordIds = new java.util.HashSet<>();

        BigDecimal generatedWasteKg = BigDecimal.ZERO;
        BigDecimal divertedWasteKg = BigDecimal.ZERO;

        for (CalculatedImpact ci : impacts) {
            EnvironmentalRecord r = ci.getRecord();
            recordIds.add(r.getId());
            ImpactType type = ci.getImpactType();
            BigDecimal v = ci.getValue();

            switch (type) {
                case CO2E -> {
                    totalCo2e = totalCo2e.add(v);
                    byCategory.merge(r.getActivityType().getCategory().name(), v, BigDecimal::add);
                    byActivity.merge(r.getActivityType().getCode(), v, BigDecimal::add);
                }
                case ENERGY -> totalEnergy = totalEnergy.add(v);
                case WATER -> totalWater = totalWater.add(v);
                case WASTE -> totalWaste = totalWaste.add(v);
            }
        }

        // recycled fraction — computed from the raw waste-category records (generated mass),
        // independent of the landfilled-mass WASTE impact.
        for (CalculatedImpact ci : impacts) {
            EnvironmentalRecord r = ci.getRecord();
            if (r.getActivityType().getCategory() != ActivityCategory.WASTE) continue;
            if (ci.getImpactType() != ImpactType.WASTE) continue; // once per record
            BigDecimal massKg;
            try {
                massKg = unitConverter.convert(r.getAmount(), r.getUnit(), "kg");
            } catch (RuntimeException e) {
                massKg = r.getAmount();
            }
            generatedWasteKg = generatedWasteKg.add(massKg);
            if (DIVERTING_ACTIVITIES.contains(r.getActivityType().getCode())) {
                divertedWasteKg = divertedWasteKg.add(massKg);
            }
        }
        BigDecimal recycledFraction = generatedWasteKg.signum() > 0
                ? divertedWasteKg.divide(generatedWasteKg, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        metric.setTotalCo2eKg(scaled(totalCo2e));
        metric.setTotalEnergyMj(scaled(totalEnergy));
        metric.setTotalWaterLitre(scaled(totalWater));
        metric.setTotalWasteKg(scaled(totalWaste));
        metric.setRecycledFraction(recycledFraction);
        metric.setCo2eByCategory(roundMap(byCategory));
        metric.setCo2eByActivity(roundMap(byActivity));
        metric.setRecordCount(recordIds.size());
        metric.setComputedAt(Instant.now());

        MonthlyMetric saved = metricRepository.save(metric);
        log.debug("Aggregated user {} {}: co2e={} energy={} water={} waste={} records={}",
                userId, period, saved.getTotalCo2eKg(), saved.getTotalEnergyMj(),
                saved.getTotalWaterLitre(), saved.getTotalWasteKg(), saved.getRecordCount());
        return saved;
    }

    private static BigDecimal scaled(BigDecimal v) {
        return v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static Map<String, BigDecimal> roundMap(Map<String, BigDecimal> in) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        in.forEach((k, v) -> out.put(k, v.setScale(SCALE, RoundingMode.HALF_UP)));
        return out;
    }
}
