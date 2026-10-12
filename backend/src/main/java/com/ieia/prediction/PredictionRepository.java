package com.ieia.prediction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PredictionRepository extends JpaRepository<Prediction, UUID> {

    Optional<Prediction> findFirstByUserIdAndTargetMetricAndHorizonMonthOrderByCreatedAtDesc(
            UUID userId, String targetMetric, LocalDate horizonMonth);

    Optional<Prediction> findFirstByUserIdAndTargetMetricOrderByCreatedAtDesc(UUID userId, String targetMetric);

    List<Prediction> findByUserIdAndTargetMetricOrderByHorizonMonth(UUID userId, String targetMetric);
}
