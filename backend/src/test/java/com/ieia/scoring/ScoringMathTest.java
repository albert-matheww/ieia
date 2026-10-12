package com.ieia.scoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScoringMathTest {

    @Test
    void workedExampleFromDocs06_carbon() {
        // docs/06 §2: good=250, poor=1200, observed C=890 -> 32.63
        double sub = ScoringMath.subScore(890, 250, 1200);
        assertThat(sub).isCloseTo(32.6316, within(1e-3));
        assertThat(ScoringMath.round(sub, 2)).isEqualTo(32.63);
    }

    @Test
    void atOrBelowGoodScores100_atOrAbovePoorScores0() {
        assertThat(ScoringMath.subScore(250, 250, 1200)).isEqualTo(100.0);
        assertThat(ScoringMath.subScore(100, 250, 1200)).isEqualTo(100.0);
        assertThat(ScoringMath.subScore(1200, 250, 1200)).isEqualTo(0.0);
        assertThat(ScoringMath.subScore(5000, 250, 1200)).isEqualTo(0.0);
    }

    @Test
    void zeroActivityIsAPerfectSubScore() {
        assertThat(ScoringMath.subScore(0, 250, 1200)).isEqualTo(100.0);
    }

    @Test
    void midpointIsLinear() {
        assertThat(ScoringMath.subScore(725, 250, 1200)).isCloseTo(50.0, within(1e-6));
    }

    @Test
    void degenerateConfigIsHandled() {
        assertThat(ScoringMath.subScore(10, 100, 100)).isEqualTo(100.0);
        assertThat(ScoringMath.subScore(200, 100, 100)).isEqualTo(0.0);
    }
}
