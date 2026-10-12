package com.ieia.report;

import com.ieia.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Builds the payload and (for PDF) renders + persists the file. Own transaction. */
@Component
public class ReportGenerationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationRunner.class);

    private final ReportRepository reportRepository;
    private final ReportPayloadBuilder payloadBuilder;
    private final PdfRenderer pdfRenderer;
    private final Path outputDir;

    public ReportGenerationRunner(ReportRepository reportRepository,
                                  ReportPayloadBuilder payloadBuilder,
                                  PdfRenderer pdfRenderer,
                                  AppProperties props) {
        this.reportRepository = reportRepository;
        this.payloadBuilder = payloadBuilder;
        this.pdfRenderer = pdfRenderer;
        this.outputDir = Path.of(props.getReports().getOutputDir());
    }

    @Async
    @Transactional
    public void runAsync(UUID reportId, List<String> include) {
        run(reportId, include);
    }

    @Transactional
    public void run(UUID reportId, List<String> include) {
        Report report = reportRepository.findById(reportId).orElse(null);
        if (report == null) return;
        try {
            report.setStatus("GENERATING");
            reportRepository.saveAndFlush(report);

            var payload = payloadBuilder.build(report.getUserId(),
                    report.getPeriodFrom(), report.getPeriodTo(), include);
            report.setPayload(payload);

            if ("PDF".equals(report.getFormat())) {
                byte[] pdf = pdfRenderer.render(report.getTitle(),
                        report.getPeriodFrom(), report.getPeriodTo(), payload);
                Files.createDirectories(outputDir);
                Path file = outputDir.resolve(reportId + ".pdf");
                Files.write(file, pdf);
                report.setFilePath(file.toString());
                report.setFileSizeBytes((long) pdf.length);
            }

            report.setStatus("READY");
            report.setGeneratedAt(Instant.now());
            reportRepository.save(report);
            log.info("Report {} generated ({}, {} bytes)", reportId, report.getFormat(),
                    report.getFileSizeBytes() == null ? 0 : report.getFileSizeBytes());
        } catch (IOException | RuntimeException e) {
            log.error("Report {} generation failed", reportId, e);
            report.setStatus("FAILED");
            report.setPayload(java.util.Map.of("error", String.valueOf(e.getMessage())));
            reportRepository.save(report);
        }
    }
}
