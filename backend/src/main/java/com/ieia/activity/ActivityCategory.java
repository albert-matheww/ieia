package com.ieia.activity;

/**
 * The <b>input</b> grouping a loggable activity belongs to
 * (docs/02-database-schema.md §3 {@code activity_types}). Distinct from the
 * scoring categories in {@code scoring/} — see docs/06-scoring-methodology.md.
 */
public enum ActivityCategory {
    ELECTRICITY,
    TRANSPORT,
    FUEL,
    WATER,
    WASTE,
    MATERIALS
}
