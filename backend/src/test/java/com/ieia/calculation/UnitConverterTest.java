package com.ieia.calculation;

import com.ieia.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitConverterTest {

    private final UnitConverter converter = new UnitConverter();

    @Test
    void identityConversion() {
        assertThat(converter.convert(new BigDecimal("123.45"), "kWh", "kWh"))
                .isEqualByComparingTo("123.45");
    }

    @Test
    void energyMwhToKwh() {
        assertThat(converter.convert(new BigDecimal("2"), "MWh", "kWh"))
                .isEqualByComparingTo("2000");
    }

    @Test
    void massTonneToKg() {
        assertThat(converter.convert(new BigDecimal("1.5"), "tonne", "kg"))
                .isEqualByComparingTo("1500");
    }

    @Test
    void volumeM3ToLitre_andKlToLitre() {
        assertThat(converter.convert(new BigDecimal("3"), "m3", "litre")).isEqualByComparingTo("3000");
        assertThat(converter.convert(new BigDecimal("0.25"), "kL", "litre")).isEqualByComparingTo("250");
    }

    @Test
    void volumeLitreToM3() {
        assertThat(converter.convert(new BigDecimal("2500"), "litre", "m3")).isEqualByComparingTo("2.5");
    }

    @Test
    void rejectsIncompatibleQuantities() {
        assertThatThrownBy(() -> converter.convert(BigDecimal.ONE, "kg", "litre"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("incompatible");
    }

    @Test
    void rejectsUnknownUnit() {
        assertThatThrownBy(() -> converter.convert(BigDecimal.ONE, "furlong", "km"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("unknown unit");
    }
}
