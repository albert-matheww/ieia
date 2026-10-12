package com.ieia.record;

import com.ieia.activity.ActivityCategory;
import com.ieia.common.dto.ApiResponse;
import com.ieia.common.dto.PageResponse;
import com.ieia.common.util.PeriodUtil;
import com.ieia.record.dto.BulkRecordRequest;
import com.ieia.record.dto.BulkRecordResult;
import com.ieia.record.dto.RecordRequest;
import com.ieia.record.dto.RecordResponse;
import com.ieia.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/environmental-records")
public class RecordController {

    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping
    public ApiResponse<PageResponse<RecordResponse>> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) ActivityCategory category,
            @RequestParam(required = false) UUID activityTypeId,
            @PageableDefault(size = 20, sort = "periodMonth") Pageable pageable) {

        LocalDate fromDate = from == null ? null : PeriodUtil.parse(from);
        LocalDate toDate = to == null ? null : PeriodUtil.parse(to);
        return ApiResponse.ok(recordService.list(
                SecurityUtils.currentUserId(), fromDate, toDate, category, activityTypeId, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<RecordResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(recordService.get(SecurityUtils.currentUserId(), id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MEMBER','ORG_OWNER','ADMIN')")
    public ResponseEntity<ApiResponse<RecordResponse>> create(@Valid @RequestBody RecordRequest req) {
        RecordResponse res = recordService.create(SecurityUtils.currentUserId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(res));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('MEMBER','ORG_OWNER','ADMIN')")
    public ResponseEntity<ApiResponse<BulkRecordResult>> bulkCreate(@Valid @RequestBody BulkRecordRequest req) {
        BulkRecordResult res = recordService.bulkCreate(SecurityUtils.currentUserId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(res));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEMBER','ORG_OWNER','ADMIN')")
    public ApiResponse<RecordResponse> update(@PathVariable UUID id, @Valid @RequestBody RecordRequest req) {
        return ApiResponse.ok(recordService.update(SecurityUtils.currentUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEMBER','ORG_OWNER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        recordService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
