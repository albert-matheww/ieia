package com.ieia.record;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnvironmentalRecordRepository
        extends JpaRepository<EnvironmentalRecord, UUID>, JpaSpecificationExecutor<EnvironmentalRecord> {

    @Query("""
           select r from EnvironmentalRecord r
           join fetch r.activityType at
           where r.id = :id and r.user.id = :userId
           """)
    Optional<EnvironmentalRecord> findByIdForUser(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
           select r from EnvironmentalRecord r
           join fetch r.activityType at
           where r.user.id = :userId and r.periodMonth = :period
           """)
    List<EnvironmentalRecord> findAllForUserAndMonth(@Param("userId") UUID userId, @Param("period") LocalDate period);

    @Query("""
           select r from EnvironmentalRecord r
           join fetch r.activityType at
           where r.user.id = :userId and r.periodMonth between :from and :to
           order by r.periodMonth asc
           """)
    List<EnvironmentalRecord> findAllForUserBetween(@Param("userId") UUID userId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    @Query("select distinct r.periodMonth from EnvironmentalRecord r where r.user.id = :userId order by r.periodMonth")
    List<LocalDate> findDistinctPeriodsForUser(@Param("userId") UUID userId);

    boolean existsByActivityType_Id(UUID activityTypeId);

    @EntityGraph(attributePaths = "activityType")
    @Override
    org.springframework.data.domain.Page<EnvironmentalRecord> findAll(
            org.springframework.data.jpa.domain.Specification<EnvironmentalRecord> spec,
            org.springframework.data.domain.Pageable pageable);
}
