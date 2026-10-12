package com.ieia.common.util;

import com.ieia.common.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * All reporting in IEIA is monthly. A "period" is a {@link LocalDate} pinned to
 * the first day of a month (matching the DB {@code period_month} column and its
 * {@code enforce_first_of_month()} trigger). This is the single place that
 * parses/normalises/iterates periods.
 */
public final class PeriodUtil {

    private PeriodUtil() {}

    /** Parse "YYYY-MM" into the first-of-month date. Throws 400 on bad input. */
    public static LocalDate parse(String yearMonth) {
        try {
            return YearMonth.parse(yearMonth.trim()).atDay(1);
        } catch (DateTimeParseException | NullPointerException e) {
            throw ApiException.validation("Invalid period '" + yearMonth + "' — expected format YYYY-MM");
        }
    }

    /** Normalise any date to the first of its month. */
    public static LocalDate firstOfMonth(LocalDate anyDayInMonth) {
        return anyDayInMonth.withDayOfMonth(1);
    }

    /** Current month, first day. */
    public static LocalDate currentPeriod() {
        return LocalDate.now().withDayOfMonth(1);
    }

    /** Format a first-of-month date as "YYYY-MM". */
    public static String format(LocalDate period) {
        return YearMonth.from(period).toString();
    }

    /** True when {@code period} is strictly after the current month. */
    public static boolean isFuture(LocalDate period) {
        return firstOfMonth(period).isAfter(currentPeriod());
    }

    public static LocalDate previousMonth(LocalDate period) {
        return firstOfMonth(period).minusMonths(1);
    }

    public static LocalDate nextMonth(LocalDate period) {
        return firstOfMonth(period).plusMonths(1);
    }

    /** Whole months between two periods (from → to inclusive of direction). */
    public static long monthsBetween(LocalDate from, LocalDate to) {
        return YearMonth.from(from).until(YearMonth.from(to), java.time.temporal.ChronoUnit.MONTHS);
    }

    /** Inclusive list of first-of-month dates from {@code from} to {@code to}. */
    public static List<LocalDate> monthsInclusive(LocalDate from, LocalDate to) {
        LocalDate a = firstOfMonth(from);
        LocalDate b = firstOfMonth(to);
        if (a.isAfter(b)) {
            throw ApiException.validation("Period range is inverted: " + format(a) + " > " + format(b));
        }
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = a; !d.isAfter(b); d = d.plusMonths(1)) {
            out.add(d);
        }
        return out;
    }
}
