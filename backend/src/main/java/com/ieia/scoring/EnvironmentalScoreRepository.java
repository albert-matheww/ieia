package com.ieia.scoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnvironmentalScoreRepository extends JpaRepository<EnvironmentalScore, UUID> {

    Optional<EnvironmentalScore> findByUserIdAndPeriodMonth(UUID userId, LocalDate periodMonth);

    List<EnvironmentalScore> findByUserIdOrderByPeriodMonth(UUID userId);

    List<EnvironmentalScore> findByUserIdAndPeriodMonthBetweenOrderByPeriodMonth(UUID userId, LocalDate from, LocalDate to);

    Optional<EnvironmentalScore> findFirstByUserIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(UUID userId, LocalDate before);

    Optional<EnvironmentalScore> findFirstByUserIdOrderByPeriodMonthDesc(UUID userId);

    void deleteByUserIdAndPeriodMonth(UUID userId, LocalDate periodMonth);
}
