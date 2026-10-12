package com.ieia.scenario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {

    Page<Scenario> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Scenario> findByIdAndUserId(UUID id, UUID userId);

    List<Scenario> findByUserIdAndIdIn(UUID userId, List<UUID> ids);
}
