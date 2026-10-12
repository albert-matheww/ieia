package com.ieia.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, UUID> {

    Optional<ActivityType> findByCode(String code);

    boolean existsByCode(String code);

    List<ActivityType> findByCategoryAndActiveTrueOrderByName(ActivityCategory category);

    List<ActivityType> findByActiveTrueOrderByCategoryAscNameAsc();

    List<ActivityType> findAllByOrderByCategoryAscNameAsc();
}
