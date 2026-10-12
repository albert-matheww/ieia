package com.ieia.scoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScoringConfigRepository extends JpaRepository<ScoringConfig, UUID> {

    Optional<ScoringConfig> findByActiveTrue();

    Optional<ScoringConfig> findByVersion(String version);
}
