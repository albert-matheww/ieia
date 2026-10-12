package com.ieia.anomaly;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnomalyRepository extends JpaRepository<Anomaly, UUID>, JpaSpecificationExecutor<Anomaly> {

    Optional<Anomaly> findByUserIdAndPeriodMonthAndMetric(UUID userId, LocalDate periodMonth, String metric);

    Optional<Anomaly> findByIdAndUserId(UUID id, UUID userId);

    List<Anomaly> findByUserIdAndStatusOrderByPeriodMonthDesc(UUID userId, String status);

    Page<Anomaly> findByUserIdOrderByPeriodMonthDesc(UUID userId, Pageable pageable);

    long countByUserIdAndStatus(UUID userId, String status);
}
