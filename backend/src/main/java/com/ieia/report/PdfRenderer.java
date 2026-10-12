package com.ieia.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Renders a report payload to a PDF using the pure-Java OpenPDF document API
 * (no HTML engine, no native deps). Nested maps become key/value tables; lists
 * of maps become row tables.
 */
@Component
public class PdfRenderer {

    private static final Color HEADING = new Color(0x0F, 0x5C, 0x3C);
    private static final Color RULE = new Color(0xD8, 0xE0, 0xDC);
    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, HEADING);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, HEADING);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, Color.DARK_GRAY);
    private static final Font BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.DARK_GRAY);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8f, Color.GRAY);

    public byte[] render(String title, LocalDate from, LocalDate to, Map<String, Object> payload) {
        // Landscape — several sections are wide row tables (scores by month, trends, predictions).
        Document doc = new Document(PageSize.A4.rotate(), 48, 48, 48, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(paragraph(title, H1, 4));
            doc.add(paragraph("Environmental Impact Report — " + from + " to " + to, BODY, 2));
            doc.add(paragraph("Generated " + String.valueOf(payload.getOrDefault("generatedAt", "")), SMALL, 12));
            doc.add(rule());

            for (Map.Entry<String, Object> section : payload.entrySet()) {
                String key = section.getKey();
                if (key.equals("generatedAt") || key.equals("reportingPeriod")) continue;
                doc.add(paragraph(prettyKey(key), H2, 6));
                renderValue(doc, section.getValue(), 0);
                doc.add(spacer(10));
            }

            doc.add(rule());
            doc.add(paragraph(
                    "IEIA — Intelligent Environmental Impact Analyzer. Figures derive from your stored "
                    + "activity data and configurable emission factors. Predictions are indicative; benchmarks "
                    + "are configurable reference values, not official standards.", SMALL, 0));
            doc.close();
            return out.toByteArray();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }

    // ---- value rendering ------------------------------------------------

    @SuppressWarnings("unchecked")
    private void renderValue(Document doc, Object value, int depth) throws Exception {
        if (value instanceof Map<?, ?> map) {
            if (isListOfScalars(map)) {
                doc.add(kvTable((Map<String, Object>) map));
            } else if (looksLikeUnavailable(map)) {
                doc.add(paragraph("(not available for this period)", SMALL, 0));
            } else {
                doc.add(kvTable(flattenScalars((Map<String, Object>) map)));
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getValue() instanceof Map || e.getValue() instanceof List) {
                        doc.add(paragraph(prettyKey(String.valueOf(e.getKey())), BODY_BOLD, 3));
                        renderValue(doc, e.getValue(), depth + 1);
                    }
                }
            }
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                doc.add(paragraph("(none)", SMALL, 0));
            } else if (list.get(0) instanceof Map) {
                doc.add(rowTable((List<Map<String, Object>>) list));
            } else {
                doc.add(paragraph(String.join(", ", list.stream().map(String::valueOf).toList()), BODY, 0));
            }
        } else {
            doc.add(paragraph(String.valueOf(value), BODY, 0));
        }
    }

    private PdfPTable kvTable(Map<String, Object> map) {
        PdfPTable t = new PdfPTable(new float[]{1f, 2f});
        t.setWidthPercentage(100);
        t.setSpacingBefore(2);
        t.setSpacingAfter(6);
        map.forEach((k, v) -> {
            t.addCell(cell(prettyKey(String.valueOf(k)), BODY_BOLD, Color.WHITE));
            t.addCell(cell(scalar(v), BODY, Color.WHITE));
        });
        return t;
    }

    private PdfPTable rowTable(List<Map<String, Object>> rows) {
        List<String> cols = new java.util.ArrayList<>(rows.get(0).keySet());
        PdfPTable t = new PdfPTable(cols.size());
        t.setWidthPercentage(100);
        t.setSpacingBefore(2);
        t.setSpacingAfter(6);
        for (String c : cols) t.addCell(cell(prettyKey(c), BODY_BOLD, new Color(0xEE, 0xF4, 0xF1)));
        for (Map<String, Object> row : rows) {
            for (String c : cols) {
                Object v = row.get(c);
                t.addCell(cell(v instanceof Map || v instanceof List ? summarise(v) : scalar(v), BODY, Color.WHITE));
            }
        }
        return t;
    }

    // ---- helpers -----------------------------------------------------

    private static boolean isListOfScalars(Map<?, ?> map) {
        return map.values().stream().noneMatch(v -> v instanceof Map || v instanceof List);
    }

    private static boolean looksLikeUnavailable(Map<?, ?> map) {
        return Boolean.TRUE.equals(map.get("unavailable"));
    }

    private static Map<String, Object> flattenScalars(Map<String, Object> map) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (!(v instanceof Map) && !(v instanceof List)) out.put(k, v);
        });
        return out;
    }

    private static String summarise(Object v) {
        String s = String.valueOf(v);
        return s.length() <= 60 ? s : s.substring(0, 57) + "…";
    }

    private static String scalar(Object v) {
        if (v == null) return "—";
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) && Math.abs(d) < 1e15
                    ? String.valueOf((long) d)
                    : String.format(java.util.Locale.ROOT, "%.2f", d);
        }
        return String.valueOf(v);
    }

    private static String prettyKey(String k) {
        String s = k.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static PdfPCell cell(String text, Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setBorderColor(RULE);
        c.setPadding(4);
        return c;
    }

    private static Paragraph paragraph(String text, Font font, float spacingAfter) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    private static Paragraph spacer(float h) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(h);
        return p;
    }

    private static Paragraph rule() {
        Paragraph p = new Paragraph();
        Rectangle line = new Rectangle(0, 0);
        line.setBorder(Rectangle.BOTTOM);
        line.setBorderColor(RULE);
        p.add(new Phrase(" "));
        p.setSpacingAfter(8);
        p.setSpacingBefore(4);
        return p;
    }
}
