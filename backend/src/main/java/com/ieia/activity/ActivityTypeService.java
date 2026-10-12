package com.ieia.activity;

import com.ieia.activity.dto.ActivityTypeDto;
import com.ieia.activity.dto.ActivityTypeRequest;
import com.ieia.common.exception.ApiException;
import com.ieia.record.EnvironmentalRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class ActivityTypeService {

    private final ActivityTypeRepository repository;
    private final EnvironmentalRecordRepository recordRepository;

    public ActivityTypeService(ActivityTypeRepository repository,
                               EnvironmentalRecordRepository recordRepository) {
        this.repository = repository;
        this.recordRepository = recordRepository;
    }

    @Transactional(readOnly = true)
    public List<ActivityTypeDto> list(ActivityCategory category, boolean activeOnly) {
        List<ActivityType> results;
        if (category != null) {
            results = repository.findByCategoryAndActiveTrueOrderByName(category);
        } else if (activeOnly) {
            results = repository.findByActiveTrueOrderByCategoryAscNameAsc();
        } else {
            results = repository.findAllByOrderByCategoryAscNameAsc();
        }
        return results.stream().map(ActivityTypeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ActivityTypeDto get(UUID id) {
        return repository.findById(id).map(ActivityTypeDto::from)
                .orElseThrow(() -> ApiException.notFound("Activity type"));
    }

    @Transactional
    public ActivityTypeDto create(ActivityTypeRequest req) {
        if (repository.existsByCode(req.code())) {
            throw ApiException.conflict("Activity type code '" + req.code() + "' already exists");
        }
        ActivityType at = new ActivityType();
        apply(at, req);
        return ActivityTypeDto.from(repository.save(at));
    }

    @Transactional
    public ActivityTypeDto update(UUID id, ActivityTypeRequest req) {
        ActivityType at = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Activity type"));
        if (!at.getCode().equals(req.code()) && repository.existsByCode(req.code())) {
            throw ApiException.conflict("Activity type code '" + req.code() + "' already exists");
        }
        apply(at, req);
        return ActivityTypeDto.from(at);
    }

    @Transactional
    public ActivityTypeDto setActive(UUID id, boolean active) {
        ActivityType at = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Activity type"));
        if (!active && recordRepository.existsByActivityType_Id(id)) {
            // Records exist — deactivation is fine (hides it from pickers) but we
            // never allow a hard delete of a type in use.
            at.setActive(false);
        } else {
            at.setActive(active);
        }
        return ActivityTypeDto.from(at);
    }

    private void apply(ActivityType at, ActivityTypeRequest req) {
        at.setCode(req.code());
        at.setCategory(ActivityCategory.valueOf(req.category()));
        at.setName(req.name());
        at.setDescription(req.description());
        at.setDefaultUnit(req.defaultUnit());
        at.setAllowedUnits(req.allowedUnits().stream().distinct().toList());
        at.setAttributeSchema(req.attributeSchema() == null ? new LinkedHashMap<>() : req.attributeSchema());
        at.setActive(req.active() == null || req.active());
        if (!at.getAllowedUnits().contains(at.getDefaultUnit())) {
            throw ApiException.validation("defaultUnit must be one of allowedUnits");
        }
    }
}
