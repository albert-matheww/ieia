package com.ieia.record;

import com.ieia.activity.ActivityCategory;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Type-safe optional filters for record search. Using the Criteria API (rather
 * than {@code (:p is null or ...)} JPQL) avoids PostgreSQL's
 * "could not determine data type of parameter" on untyped null binds.
 */
public final class RecordSpecifications {

    private RecordSpecifications() {}

    public static Specification<EnvironmentalRecord> ownedBy(UUID userId) {
        return (root, q, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<EnvironmentalRecord> periodFrom(LocalDate from) {
        return from == null ? null
                : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("periodMonth"), from);
    }

    public static Specification<EnvironmentalRecord> periodTo(LocalDate to) {
        return to == null ? null
                : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("periodMonth"), to);
    }

    public static Specification<EnvironmentalRecord> inCategory(ActivityCategory category) {
        return category == null ? null
                : (root, q, cb) -> cb.equal(root.get("activityType").get("category"), category);
    }

    public static Specification<EnvironmentalRecord> ofActivityType(UUID activityTypeId) {
        return activityTypeId == null ? null
                : (root, q, cb) -> cb.equal(root.get("activityType").get("id"), activityTypeId);
    }

    public static Specification<EnvironmentalRecord> build(UUID userId, LocalDate from, LocalDate to,
                                                          ActivityCategory category, UUID activityTypeId) {
        return Specification.where(ownedBy(userId))
                .and(periodFrom(from))
                .and(periodTo(to))
                .and(inCategory(category))
                .and(ofActivityType(activityTypeId));
    }
}
