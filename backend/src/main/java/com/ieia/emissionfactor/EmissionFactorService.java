package com.ieia.emissionfactor;

import com.ieia.activity.ActivityType;
import com.ieia.activity.ActivityTypeRepository;
import com.ieia.common.exception.ApiException;
import com.ieia.emissionfactor.dto.EmissionFactorDto;
import com.ieia.emissionfactor.dto.EmissionFactorRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EmissionFactorService {

    private final EmissionFactorRepository repository;
    private final ActivityTypeRepository activityTypeRepository;

    public EmissionFactorService(EmissionFactorRepository repository,
                                 ActivityTypeRepository activityTypeRepository) {
        this.repository = repository;
        this.activityTypeRepository = activityTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<EmissionFactorDto> search(UUID activityTypeId, String impactType, String region) {
        ImpactType it = impactType == null ? null : ImpactType.valueOf(impactType);
        return repository.findAllWithActivityType().stream()
                .filter(f -> activityTypeId == null || f.getActivityType().getId().equals(activityTypeId))
                .filter(f -> it == null || f.getImpactType() == it)
                .filter(f -> region == null || region.isBlank() || f.getRegion().equalsIgnoreCase(region))
                .map(EmissionFactorDto::from)
                .toList();
    }

    @Transactional
    public EmissionFactorDto create(EmissionFactorRequest req) {
        EmissionFactor f = new EmissionFactor();
        apply(f, req);
        try {
            return EmissionFactorDto.from(repository.saveAndFlush(f));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("A factor already exists for that activity / impact / region / unit / date");
        }
    }

    @Transactional
    public EmissionFactorDto update(UUID id, EmissionFactorRequest req) {
        EmissionFactor f = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Emission factor"));
        apply(f, req);
        try {
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("A factor already exists for that activity / impact / region / unit / date");
        }
        return EmissionFactorDto.from(f);
    }

    @Transactional
    public void deactivate(UUID id) {
        EmissionFactor f = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Emission factor"));
        f.setActive(false);
    }

    private void apply(EmissionFactor f, EmissionFactorRequest req) {
        ActivityType at = activityTypeRepository.findById(req.activityTypeId())
                .orElseThrow(() -> ApiException.unprocessable("Unknown activityTypeId " + req.activityTypeId()));
        if (req.effectiveTo() != null && !req.effectiveTo().isAfter(req.effectiveFrom())) {
            throw ApiException.validation("effectiveTo must be after effectiveFrom");
        }
        f.setActivityType(at);
        f.setImpactType(ImpactType.valueOf(req.impactType()));
        f.setUnitFrom(req.unitFrom().trim());
        f.setUnitTo(req.unitTo().trim());
        f.setValue(req.value());
        f.setRegion(req.region() == null || req.region().isBlank() ? "GLOBAL" : req.region().trim());
        f.setSource(req.source().trim());
        f.setEffectiveFrom(req.effectiveFrom());
        f.setEffectiveTo(req.effectiveTo());
        f.setActive(true);
    }
}
