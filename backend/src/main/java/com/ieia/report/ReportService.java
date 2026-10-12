package com.ieia.report;

import com.ieia.common.dto.PageResponse;
import com.ieia.common.exception.ApiException;
import com.ieia.common.util.PeriodUtil;
import com.ieia.report.dto.ReportDtos;
import com.ieia.user.User;
import com.ieia.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {

    private final ReportRepository repository;
    private final ReportGenerationRunner runner;
    private final UserRepository userRepository;
    private final boolean async;

    public ReportService(ReportRepository repository,
                         ReportGenerationRunner runner,
                         UserRepository userRepository,
                         @Value("${ieia.reports.async:true}") boolean async) {
        this.repository = repository;
        this.runner = runner;
        this.userRepository = userRepository;
        this.async = async;
    }

    @Transactional
    public ReportDtos.ReportSummary create(UUID userId, ReportDtos.CreateReportRequest req) {
        LocalDate from = PeriodUtil.parse(req.from());
        LocalDate to = PeriodUtil.parse(req.to());
        if (from.isAfter(to)) throw ApiException.validation("from must not be after to");

        User user = userRepository.findByIdWithOrganization(userId).orElseThrow();
        Report report = new Report();
        report.setUserId(userId);
        report.setOrganizationId(user.getOrganization() == null ? null : user.getOrganization().getId());
        report.setTitle(req.title() == null || req.title().isBlank()
                ? "Environmental Impact Report " + req.from() + "–" + req.to()
                : req.title().trim());
        report.setPeriodFrom(from);
        report.setPeriodTo(to);
        report.setFormat(req.format() == null ? "PDF" : req.format());
        report.setStatus("PENDING");
        repository.saveAndFlush(report);

        UUID id = report.getId();
        List<String> include = req.include();
        if (async && "PDF".equals(report.getFormat())) {
            // Fire the async job only once this transaction has committed — otherwise the
            // worker thread can race ahead and its findById() sees no row yet (staying PENDING).
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        runner.runAsync(id, include);
                    }
                });
            } else {
                runner.runAsync(id, include);
            }
        } else {
            runner.run(id, include);
        }
        return summary(repository.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public ReportDtos.ReportDetail get(UUID userId, UUID id) {
        Report r = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Report"));
        return new ReportDtos.ReportDetail(summary(r),
                "READY".equals(r.getStatus()) ? r.getPayload() : java.util.Map.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportDtos.ReportSummary> list(UUID userId, Pageable pageable) {
        return PageResponse.from(repository.findByUserIdOrderByCreatedAtDesc(userId, pageable), this::summary);
    }

    @Transactional(readOnly = true)
    public DownloadFile download(UUID userId, UUID id) {
        Report r = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Report"));
        if (!"PDF".equals(r.getFormat())) {
            throw ApiException.validation("This report is JSON — use GET /api/reports/{id}");
        }
        if (!"READY".equals(r.getStatus()) || r.getFilePath() == null) {
            throw ApiException.unprocessable("Report is not ready yet (status " + r.getStatus() + ")");
        }
        try {
            byte[] bytes = Files.readAllBytes(Path.of(r.getFilePath()));
            return new DownloadFile(sanitize(r.getTitle()) + ".pdf", bytes);
        } catch (IOException e) {
            throw new ApiException(com.ieia.common.exception.ErrorCode.INTERNAL, "Report file is missing");
        }
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Report r = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Report"));
        if (r.getFilePath() != null) {
            try {
                Files.deleteIfExists(Path.of(r.getFilePath()));
            } catch (IOException ignored) {
            }
        }
        repository.delete(r);
    }

    public record DownloadFile(String filename, byte[] bytes) {}

    private ReportDtos.ReportSummary summary(Report r) {
        return new ReportDtos.ReportSummary(
                r.getId(), r.getTitle(), PeriodUtil.format(r.getPeriodFrom()), PeriodUtil.format(r.getPeriodTo()),
                r.getFormat(), r.getStatus(), r.getFileSizeBytes(), r.getGeneratedAt(), r.getCreatedAt());
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
