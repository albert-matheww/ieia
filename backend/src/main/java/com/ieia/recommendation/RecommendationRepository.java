package com.ieia.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    List<Recommendation> findByUserIdAndPeriodMonthOrderByRank(UUID userId, LocalDate periodMonth);

    void deleteByUserIdAndPeriodMonth(UUID userId, LocalDate periodMonth);

    Optional<Recommendation> findByIdAndUserId(UUID id, UUID userId);

    @org.springframework.data.jpa.repository.Query(
            "select max(r.periodMonth) from Recommendation r where r.userId = :userId")
    LocalDate latestPeriod(UUID userId);
}
