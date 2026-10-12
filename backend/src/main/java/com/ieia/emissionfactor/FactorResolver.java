package com.ieia.emissionfactor;

import com.ieia.activity.ActivityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks the single {@link EmissionFactor} that applies to an activity for a
 * given region and reporting month.
 *
 * <p>Resolution order (most specific wins):
 * <ol>
 *   <li>factor must cover the {@code period} ({@code effective_from ≤ period <
 *       effective_to})</li>
 *   <li>prefer an exact {@code region} match over a {@code GLOBAL} fallback</li>
 *   <li>among those, prefer the latest {@code effective_from}</li>
 * </ol>
 * Returns empty when no factor exists — the caller records the impact as
 * uncalculated with a warning rather than guessing.
 */
@Component
public class FactorResolver {

    private static final Logger log = LoggerFactory.getLogger(FactorResolver.class);
    private static final String GLOBAL = "GLOBAL";

    private final EmissionFactorRepository repository;

    public FactorResolver(EmissionFactorRepository repository) {
        this.repository = repository;
    }

    public Optional<EmissionFactor> resolve(ActivityType activityType, ImpactType impactType,
                                            String region, LocalDate period) {
        List<EmissionFactor> candidates = repository.findCandidates(activityType.getId(), impactType);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        String wanted = region == null || region.isBlank() ? GLOBAL : region;

        Comparator<EmissionFactor> mostSpecific = Comparator
                .comparingInt((EmissionFactor f) -> f.getRegion().equalsIgnoreCase(wanted) ? 1 : 0)
                .thenComparing(EmissionFactor::getEffectiveFrom);

        Optional<EmissionFactor> match = candidates.stream()
                .filter(f -> f.coversPeriod(period))
                .filter(f -> f.getRegion().equalsIgnoreCase(wanted) || f.getRegion().equalsIgnoreCase(GLOBAL))
                .max(mostSpecific);

        if (match.isEmpty()) {
            log.warn("No emission factor for activity={} impact={} region={} period={}",
                    activityType.getCode(), impactType, wanted, period);
        }
        return match;
    }
}
