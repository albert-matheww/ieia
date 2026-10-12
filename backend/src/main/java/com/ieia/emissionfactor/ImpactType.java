package com.ieia.emissionfactor;

/**
 * The <b>output</b> dimension an emission factor converts an activity into.
 * One activity can produce several (e.g. electricity → CO2E and ENERGY).
 */
public enum ImpactType {
    CO2E("kgCO2e"),
    ENERGY("MJ"),
    WATER("litre"),
    WASTE("kg");

    private final String canonicalUnit;

    ImpactType(String canonicalUnit) {
        this.canonicalUnit = canonicalUnit;
    }

    public String canonicalUnit() {
        return canonicalUnit;
    }
}
