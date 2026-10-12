package com.ieia.record;

import com.ieia.activity.ActivityType;
import com.ieia.activity.ActivityCategory;
import com.ieia.activity.ActivityTypeRepository;
import com.ieia.calculation.CalculatedImpact;
import com.ieia.calculation.CalculatedImpactRepository;
import com.ieia.calculation.ImpactCalculationEngine;
import com.ieia.calculation.MonthChangedEvent;
import com.ieia.common.dto.PageResponse;
import com.ieia.common.exception.ApiException;
import com.ieia.common.util.PeriodUtil;
import com.ieia.record.dto.BulkRecordRequest;
import com.ieia.record.dto.BulkRecordResult;
import com.ieia.record.dto.RecordRequest;
import com.ieia.record.dto.RecordResponse;
import com.ieia.user.User;
import com.ieia.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecordService {

    private static final Logger log = LoggerFactory.getLogger(RecordService.class);

    private final EnvironmentalRecordRepository recordRepository;
    private final CalculatedImpactRepository impactRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final UserRepository userRepository;
    private final ImpactCalculationEngine calculationEngine;
    private final RecordAttributeValidator attributeValidator;
    private final ApplicationEventPublisher events;

    public RecordService(EnvironmentalRecordRepository recordRepository,
                         CalculatedImpactRepository impactRepository,
                         ActivityTypeRepository activityTypeRepository,
                         UserRepository userRepository,
                         ImpactCalculationEngine calculationEngine,
                         RecordAttributeValidator attributeValidator,
                         ApplicationEventPublisher events) {
        this.recordRepository = recordRepository;
        this.impactRepository = impactRepository;
        this.activityTypeRepository = activityTypeRepository;
        this.userRepository = userRepository;
        this.calculationEngine = calculationEngine;
        this.attributeValidator = attributeValidator;
        this.events = events;
    }

    // ---- queries --------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<RecordResponse> list(UUID userId, LocalDate from, LocalDate to,
                                             ActivityCategory category, UUID activityTypeId, Pageable pageable) {
        Page<EnvironmentalRecord> page = recordRepository.findAll(
                RecordSpecifications.build(userId, from, to, category, activityTypeId), pageable);
        List<UUID> ids = page.getContent().stream().map(EnvironmentalRecord::getId).toList();
        Map<UUID, List<CalculatedImpact>> impactsByRecord = ids.isEmpty() ? Map.of()
                : impactRepository.findByRecord_IdInOrderByImpactType(ids).stream()
                    .collect(Collectors.groupingBy(ci -> ci.getRecord().getId()));
        return PageResponse.from(page, r ->
                RecordResponse.from(r, impactsByRecord.getOrDefault(r.getId(), List.of()), List.of()));
    }

    @Transactional(readOnly = true)
    public RecordResponse get(UUID userId, UUID id) {
        EnvironmentalRecord r = recordRepository.findByIdForUser(id, userId)
                .orElseThrow(() -> ApiException.notFound("Environmental record"));
        return RecordResponse.from(r, impactRepository.findByRecord_IdOrderByImpactType(id), List.of());
    }

    // ---- commands ------------------------------------------------------

    @Transactional
    public RecordResponse create(UUID userId, RecordRequest req) {
        User user = userRepository.findByIdWithOrganization(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        ActivityType activityType = loadActiveActivityType(req.activityTypeId());
        LocalDate period = validatePeriod(req.period());
        validateUnit(activityType, req.unit());

        EnvironmentalRecord record = new EnvironmentalRecord();
        record.setUser(user);
        record.setOrganization(user.getOrganization());
        record.setActivityType(activityType);
        record.setPeriodMonth(period);
        record.setAmount(req.amount());
        record.setUnit(req.unit().trim());
        record.setLocation(trimToNull(req.location()));
        record.setSourceDetail(trimToNull(req.sourceDetail()));
        record.setAttributes(attributeValidator.validateAndNormalise(activityType, req.attributes()));
        record.setNote(trimToNull(req.note()));
        record.setCreatedBy(userId);
        recordRepository.save(record);

        RecomputeResult rc = recompute(record);
        publishMonthChanged(userId, period, "record-created");

        log.debug("Created record {} for user {} ({} {}) -> {} impacts",
                record.getId(), userId, req.amount(), req.unit(), rc.impacts().size());
        return RecordResponse.from(record, rc.impacts(), rc.warnings());
    }

    @Transactional
    public RecordResponse update(UUID userId, UUID id, RecordRequest req) {
        EnvironmentalRecord record = recordRepository.findByIdForUser(id, userId)
                .orElseThrow(() -> ApiException.notFound("Environmental record"));
        ActivityType activityType = loadActiveActivityType(req.activityTypeId());
        LocalDate newPeriod = validatePeriod(req.period());
        validateUnit(activityType, req.unit());

        LocalDate oldPeriod = record.getPeriodMonth();

        record.setActivityType(activityType);
        record.setPeriodMonth(newPeriod);
        record.setAmount(req.amount());
        record.setUnit(req.unit().trim());
        record.setLocation(trimToNull(req.location()));
        record.setSourceDetail(trimToNull(req.sourceDetail()));
        record.setAttributes(attributeValidator.validateAndNormalise(activityType, req.attributes()));
        record.setNote(trimToNull(req.note()));

        RecomputeResult rc = recompute(record);

        publishMonthChanged(userId, newPeriod, "record-updated");
        if (!oldPeriod.equals(newPeriod)) {
            publishMonthChanged(userId, oldPeriod, "record-moved-out");
        }
        return RecordResponse.from(record, rc.impacts(), rc.warnings());
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        EnvironmentalRecord record = recordRepository.findByIdForUser(id, userId)
                .orElseThrow(() -> ApiException.notFound("Environmental record"));
        LocalDate period = record.getPeriodMonth();
        impactRepository.deleteByRecordId(record.getId());
        recordRepository.delete(record);
        publishMonthChanged(userId, period, "record-deleted");
    }

    @Transactional
    public BulkRecordResult bulkCreate(UUID userId, BulkRecordRequest bulk) {
        List<RecordResponse> created = new ArrayList<>();
        List<BulkRecordResult.FailedItem> failures = new ArrayList<>();
        for (int i = 0; i < bulk.items().size(); i++) {
            try {
                created.add(create(userId, bulk.items().get(i)));
            } catch (ApiException e) {
                failures.add(new BulkRecordResult.FailedItem(i, e.getMessage()));
            }
        }
        return new BulkRecordResult(created.size(), failures.size(), created, failures);
    }

    // ---- internals ----------------------------------------------------

    private record RecomputeResult(List<CalculatedImpact> impacts, List<String> warnings) {}

    /** Delete existing impacts for the record and write the freshly computed set. */
    private RecomputeResult recompute(EnvironmentalRecord record) {
        impactRepository.deleteByRecordId(record.getId());
        impactRepository.flush();
        ImpactCalculationEngine.CalculationResult result = calculationEngine.calculate(record);
        List<CalculatedImpact> saved = new ArrayList<>();
        for (CalculatedImpact impact : result.impacts()) {
            saved.add(impactRepository.save(impact));
        }
        return new RecomputeResult(saved, result.warnings());
    }

    private void publishMonthChanged(UUID userId, LocalDate period, String reason) {
        events.publishEvent(new MonthChangedEvent(userId, period, reason));
    }

    private ActivityType loadActiveActivityType(UUID activityTypeId) {
        ActivityType at = activityTypeRepository.findById(activityTypeId)
                .orElseThrow(() -> ApiException.unprocessable("Unknown activityTypeId " + activityTypeId));
        if (!at.isActive()) {
            throw ApiException.unprocessable("Activity type '" + at.getCode() + "' is not active");
        }
        return at;
    }

    private LocalDate validatePeriod(String period) {
        LocalDate p = PeriodUtil.parse(period);
        if (PeriodUtil.isFuture(p)) {
            throw ApiException.unprocessable("period " + period + " is in the future");
        }
        return p;
    }

    private void validateUnit(ActivityType activityType, String unit) {
        if (!activityType.allowsUnit(unit)) {
            throw ApiException.unprocessable("unit '" + unit + "' is not allowed for "
                    + activityType.getCode() + "; allowed: " + activityType.getAllowedUnits());
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
