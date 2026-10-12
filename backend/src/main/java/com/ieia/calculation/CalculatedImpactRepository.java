package com.ieia.calculation;

import com.ieia.emissionfactor.ImpactType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CalculatedImpactRepository extends JpaRepository<CalculatedImpact, UUID> {

    @Modifying
    @Query("delete from CalculatedImpact ci where ci.record.id = :recordId")
    void deleteByRecordId(@Param("recordId") UUID recordId);

    List<CalculatedImpact> findByRecord_IdOrderByImpactType(UUID recordId);

    List<CalculatedImpact> findByRecord_IdInOrderByImpactType(java.util.Collection<UUID> recordIds);

    List<CalculatedImpact> findByUserIdAndPeriodMonth(UUID userId, LocalDate periodMonth);

    List<CalculatedImpact> findByUserIdAndPeriodMonthBetween(UUID userId, LocalDate from, LocalDate to);

    /** Impacts for a user+month with their record and activity type eagerly loaded (for aggregation). */
    @Query("""
           select ci from CalculatedImpact ci
           join fetch ci.record r
           join fetch r.activityType at
           where ci.userId = :userId and ci.periodMonth = :period
           """)
    List<CalculatedImpact> findDetailedForUserMonth(@Param("userId") UUID userId, @Param("period") LocalDate period);

    @Query("""
           select ci from CalculatedImpact ci
           join fetch ci.record r
           join fetch r.activityType at
           where ci.userId = :userId and ci.periodMonth between :from and :to
           """)
    List<CalculatedImpact> findDetailedForUserBetween(@Param("userId") UUID userId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    interface ImpactSum {
        ImpactType getImpactType();
        java.math.BigDecimal getTotal();
    }

    @Query("""
           select ci.impactType as impactType, coalesce(sum(ci.value), 0) as total
           from CalculatedImpact ci
           where ci.userId = :userId and ci.periodMonth = :period
           group by ci.impactType
           """)
    List<ImpactSum> sumByImpactTypeForMonth(@Param("userId") UUID userId, @Param("period") LocalDate period);
}
