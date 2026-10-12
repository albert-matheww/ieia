package com.ieia.scoring;

/**
 * The pure, hand-recomputable maths behind the environmental score
 * (docs/06-scoring-methodology.md §2). No Spring, no persistence.
 */
public final class ScoringMath {

    private ScoringMath() {}

    /**
     * Normalise a metric value {@code m} to a 0–100 sub-score against configurable
     * {@code good} and {@code poor} thresholds (with {@code good < poor}, lower
     * impact = higher score):
     * <pre>sub(m) = clamp( 100 · (poor − m) / (poor − good), 0, 100 )</pre>
     */
    public static double subScore(double m, double good, double poor) {
        if (poor <= good) {
            // Degenerate config — treat "at or below good" as perfect, else zero.
            return m <= good ? 100.0 : 0.0;
        }
        double raw = 100.0 * (poor - m) / (poor - good);
        return clamp(raw, 0.0, 100.0);
    }

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static double round(double v, int dp) {
        double f = Math.pow(10, dp);
        return Math.round(v * f) / f;
    }
}
