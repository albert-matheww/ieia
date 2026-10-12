package com.ieia.prediction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ModelRunRepository extends JpaRepository<ModelRun, UUID> {

    Page<ModelRun> findByModelTypeOrderByTrainedAtDesc(String modelType, Pageable pageable);
}
