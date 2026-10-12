package com.ieia.calculation;

import com.ieia.common.exception.ApiException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

/**
 * Converts an activity amount from the unit the user entered to the unit a
 * chosen {@link com.ieia.emissionfactor.EmissionFactor} expects.
 *
 * <p>Only <b>pure dimensional</b> conversions are supported (energy, mass,
 * volume, distance, count). Substance-specific conversions (e.g. litres of LPG →
 * kg) are deliberately excluded — the reference data restricts each activity's
 * {@code allowed_units} to a single physical dimension so this stays exact.
 */
@Component
public class UnitConverter {

    /** unit (lower-case) → [dimension, multiplier to the dimension's base unit] */
    private static final Map<String, Object[]> UNITS = Map.ofEntries(
            Map.entry("kwh",   new Object[]{"ENERGY_ELEC", new BigDecimal("1")}),
            Map.entry("mwh",   new Object[]{"ENERGY_ELEC", new BigDecimal("1000")}),
            Map.entry("mj",    new Object[]{"ENERGY", new BigDecimal("1")}),
            Map.entry("kg",    new Object[]{"MASS", new BigDecimal("1")}),
            Map.entry("tonne", new Object[]{"MASS", new BigDecimal("1000")}),
            Map.entry("t",     new Object[]{"MASS", new BigDecimal("1000")}),
            Map.entry("litre", new Object[]{"VOLUME", new BigDecimal("1")}),
            Map.entry("l",     new Object[]{"VOLUME", new BigDecimal("1")}),
            Map.entry("m3",    new Object[]{"VOLUME", new BigDecimal("1000")}),
            Map.entry("kl",    new Object[]{"VOLUME", new BigDecimal("1000")}),
            Map.entry("km",    new Object[]{"DISTANCE", new BigDecimal("1")}),
            Map.entry("mile",  new Object[]{"DISTANCE", new BigDecimal("1.609344")}),
            Map.entry("trip",  new Object[]{"COUNT", new BigDecimal("1")}),
            Map.entry("unit",  new Object[]{"COUNT", new BigDecimal("1")})
    );

    public BigDecimal convert(BigDecimal amount, String fromUnit, String toUnit) {
        if (fromUnit.equalsIgnoreCase(toUnit)) {
            return amount;
        }
        Object[] from = UNITS.get(fromUnit.toLowerCase());
        Object[] to = UNITS.get(toUnit.toLowerCase());
        if (from == null || to == null) {
            throw ApiException.unprocessable(
                    "Cannot convert between units '" + fromUnit + "' and '" + toUnit + "' — unknown unit");
        }
        if (!from[0].equals(to[0])) {
            throw ApiException.unprocessable(
                    "Cannot convert '" + fromUnit + "' to '" + toUnit + "' — incompatible physical quantities");
        }
        BigDecimal fromBase = (BigDecimal) from[1];
        BigDecimal toBase = (BigDecimal) to[1];
        return amount.multiply(fromBase, MathContext.DECIMAL64)
                .divide(toBase, MathContext.DECIMAL64);
    }

    public boolean isKnownUnit(String unit) {
        return unit != null && UNITS.containsKey(unit.toLowerCase());
    }
}
