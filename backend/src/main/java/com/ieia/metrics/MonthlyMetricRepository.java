package com.ieia.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyMetricRepository extends JpaRepository<MonthlyMetric, UUID> {

    Optional<MonthlyMetric> findByUserIdAndPeriodMonth(UUID userId, LocalDate periodMonth);

    List<MonthlyMetric> findByUserIdOrderByPeriodMonth(UUID userId);

    List<MonthlyMetric> findByUserIdAndPeriodMonthBetweenOrderByPeriodMonth(UUID userId, LocalDate from, LocalDate to);

    Optional<MonthlyMetric> findFirstByUserIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(UUID userId, LocalDate before);

    Optional<MonthlyMetric> findFirstByUserIdOrderByPeriodMonthDesc(UUID userId);

    void deleteByUserIdAndPeriodMonth(UUID userId, LocalDate periodMonth);
}
