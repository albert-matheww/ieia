package com.ieia.metrics;

import com.ieia.activity.ActivityCategory;
import com.ieia.activity.ActivityType;
import com.ieia.activity.ActivityTypeRepository;
import com.ieia.calculation.CalculatedImpact;
import com.ieia.calculation.UnitConverter;
import com.ieia.emissionfactor.ImpactType;
import com.ieia.metrics.dto.DashboardResponse.ActivityEmission;
import com.ieia.metrics.dto.DashboardResponse.CategoryEmission;
import com.ieia.metrics.dto.DashboardResponse.WasteSlice;
import com.ieia.record.EnvironmentalRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared breakdown builders used by both the dashboard and the impact endpoints. */
@Component
public class ImpactAnalyticsSupport {

    private static final Map<String, String> WASTE_METHOD = Map.of(
            "waste_mixed", "Landfill",
            "waste_recycled", "Recycling",
            "waste_organic_compost", "Composting",
            "waste_incinerated", "Incineration");

    private final ActivityTypeRepository activityTypeRepository;
    private final UnitConverter unitConverter;

    public ImpactAnalyticsSupport(ActivityTypeRepository activityTypeRepository, UnitConverter unitConverter) {
        this.activityTypeRepository = activityTypeRepository;
        this.unitConverter = unitConverter;
    }

    public Map<String, String> activityNamesByCode() {
        Map<String, String> map = new LinkedHashMap<>();
        for (ActivityType at : activityTypeRepository.findAll()) {
            map.put(at.getCode(), at.getName());
        }
        return map;
    }

    /** Sum the {@code co2e_by_category} maps of several monthly metrics. */
    public List<CategoryEmission> emissionsByCategory(List<MonthlyMetric> metrics) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (MonthlyMetric m : metrics) {
            m.getCo2eByCategory().forEach((k, v) -> totals.merge(k, v, BigDecimal::add));
        }
        BigDecimal grand = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategoryEmission> out = new ArrayList<>();
        totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> out.add(new CategoryEmission(e.getKey(), scale(e.getValue()), pct(e.getValue(), grand))));
        return out;
    }

    public List<ActivityEmission> emissionsByActivity(List<MonthlyMetric> metrics, int limit) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (MonthlyMetric m : metrics) {
            m.getCo2eByActivity().forEach((k, v) -> totals.merge(k, v, BigDecimal::add));
        }
        BigDecimal grand = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, String> names = activityNamesByCode();
        List<ActivityEmission> out = new ArrayList<>();
        totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(limit <= 0 ? Long.MAX_VALUE : limit)
                .forEach(e -> out.add(new ActivityEmission(
                        e.getKey(), names.getOrDefault(e.getKey(), e.getKey()),
                        scale(e.getValue()), pct(e.getValue(), grand))));
        return out;
    }

    /** Waste generated (mass, kg) grouped by disposal route, from record-level detail. */
    public List<WasteSlice> wasteComposition(List<CalculatedImpact> detailedImpacts) {
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        java.util.Set<java.util.UUID> seenRecords = new java.util.HashSet<>();
        for (CalculatedImpact ci : detailedImpacts) {
            EnvironmentalRecord r = ci.getRecord();
            if (r.getActivityType().getCategory() != ActivityCategory.WASTE) continue;
            if (!seenRecords.add(r.getId())) continue;
            BigDecimal kg;
            try {
                kg = unitConverter.convert(r.getAmount(), r.getUnit(), "kg");
            } catch (RuntimeException e) {
                kg = r.getAmount();
            }
            String method = WASTE_METHOD.getOrDefault(r.getActivityType().getCode(), "Other");
            byMethod.merge(method, kg, BigDecimal::add);
        }
        List<WasteSlice> out = new ArrayList<>();
        byMethod.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> out.add(new WasteSlice(e.getKey(), scale(e.getValue()))));
        return out;
    }

    public List<com.ieia.metrics.dto.DashboardResponse.Contributor> topContributors(
            List<MonthlyMetric> metrics, int limit) {
        Map<String, BigDecimal> byActivity = new LinkedHashMap<>();
        Map<String, String> activityCategory = new LinkedHashMap<>();
        for (ActivityType at : activityTypeRepository.findAll()) {
            activityCategory.put(at.getCode(), at.getCategory().name());
        }
        for (MonthlyMetric m : metrics) {
            m.getCo2eByActivity().forEach((k, v) -> byActivity.merge(k, v, BigDecimal::add));
        }
        BigDecimal grand = byActivity.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, String> names = activityNamesByCode();
        List<com.ieia.metrics.dto.DashboardResponse.Contributor> out = new ArrayList<>();
        byActivity.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(limit)
                .forEach(e -> out.add(new com.ieia.metrics.dto.DashboardResponse.Contributor(
                        activityCategory.getOrDefault(e.getKey(), "OTHER"),
                        e.getKey(),
                        names.getOrDefault(e.getKey(), e.getKey()),
                        scale(e.getValue()),
                        pct(e.getValue(), grand))));
        return out;
    }

    public static BigDecimal scale(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return BigDecimal.ZERO;
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal deltaPct(BigDecimal now, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) return null;
        return now.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }
}
