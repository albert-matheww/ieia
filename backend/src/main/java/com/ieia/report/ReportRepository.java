package com.ieia.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Report> findByIdAndUserId(UUID id, UUID userId);
}
