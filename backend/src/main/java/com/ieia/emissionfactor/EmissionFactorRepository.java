package com.ieia.emissionfactor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EmissionFactorRepository extends JpaRepository<EmissionFactor, UUID> {

    /**
     * Candidate factors for an activity + dimension, active only, newest first.
     * {@link FactorResolver} filters by region and effective window in memory —
     * the candidate set per (activity, impact) is tiny.
     */
    @Query("""
           select f from EmissionFactor f
           join fetch f.activityType at
           where at.id = :activityTypeId and f.impactType = :impactType and f.active = true
           order by f.effectiveFrom desc
           """)
    List<EmissionFactor> findCandidates(@Param("activityTypeId") UUID activityTypeId,
                                        @Param("impactType") ImpactType impactType);

    @Query("""
           select f from EmissionFactor f
           join fetch f.activityType at
           where at.id = :activityTypeId and f.active = true
           order by f.impactType asc, f.effectiveFrom desc
           """)
    List<EmissionFactor> findActiveByActivityType(@Param("activityTypeId") UUID activityTypeId);

    @Query("""
           select f from EmissionFactor f
           join fetch f.activityType at
           order by at.code asc, f.impactType asc, f.effectiveFrom desc
           """)
    List<EmissionFactor> findAllWithActivityType();
}
