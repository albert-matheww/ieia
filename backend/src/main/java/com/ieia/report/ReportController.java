package com.ieia.report;

import com.ieia.common.dto.ApiResponse;
import com.ieia.common.dto.PageResponse;
import com.ieia.report.dto.ReportDtos;
import com.ieia.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ReportDtos.ReportSummary>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(reportService.list(SecurityUtils.currentUserId(), pageable));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReportDtos.ReportSummary>> create(
            @Valid @RequestBody ReportDtos.CreateReportRequest req) {
        ReportDtos.ReportSummary summary = reportService.create(SecurityUtils.currentUserId(), req);
        return ResponseEntity.accepted().body(ApiResponse.ok(summary));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReportDtos.ReportDetail> get(@PathVariable UUID id) {
        return ApiResponse.ok(reportService.get(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable UUID id) {
        ReportService.DownloadFile file = reportService.download(SecurityUtils.currentUserId(), id);
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.bytes().length)
                .body(new ByteArrayResource(file.bytes()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        reportService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
