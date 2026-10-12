package com.ieia.emissionfactor;

import com.ieia.activity.ActivityCategory;
import com.ieia.activity.ActivityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FactorResolverTest {

    private EmissionFactorRepository repository;
    private FactorResolver resolver;
    private ActivityType electricity;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(EmissionFactorRepository.class);
        resolver = new FactorResolver(repository);
        electricity = new ActivityType();
        electricity.setId(UUID.randomUUID());
        electricity.setCode("electricity_grid");
        electricity.setCategory(ActivityCategory.ELECTRICITY);
    }

    private EmissionFactor factor(String region, BigDecimal value, LocalDate from, LocalDate to) {
        EmissionFactor f = new EmissionFactor();
        f.setId(UUID.randomUUID());
        f.setActivityType(electricity);
        f.setImpactType(ImpactType.CO2E);
        f.setUnitFrom("kWh");
        f.setUnitTo("kgCO2e");
        f.setValue(value);
        f.setRegion(region);
        f.setSource("test");
        f.setEffectiveFrom(from);
        f.setEffectiveTo(to);
        f.setActive(true);
        return f;
    }

    @Test
    void prefersRegionSpecificOverGlobal() {
        var global = factor("GLOBAL", new BigDecimal("0.475"), LocalDate.of(2023, 1, 1), null);
        var india = factor("IN", new BigDecimal("0.716"), LocalDate.of(2023, 1, 1), null);
        Mockito.when(repository.findCandidates(electricity.getId(), ImpactType.CO2E))
                .thenReturn(List.of(global, india));

        var resolved = resolver.resolve(electricity, ImpactType.CO2E, "IN", LocalDate.of(2026, 5, 1));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getValue()).isEqualByComparingTo("0.716");
    }

    @Test
    void fallsBackToGlobalWhenNoRegionMatch() {
        var global = factor("GLOBAL", new BigDecimal("0.475"), LocalDate.of(2023, 1, 1), null);
        Mockito.when(repository.findCandidates(electricity.getId(), ImpactType.CO2E))
                .thenReturn(List.of(global));

        var resolved = resolver.resolve(electricity, ImpactType.CO2E, "IN", LocalDate.of(2026, 5, 1));

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getRegion()).isEqualTo("GLOBAL");
    }

    @Test
    void picksLatestEffectiveFactorWithinWindow() {
        var older = factor("GLOBAL", new BigDecimal("0.90"), LocalDate.of(2018, 1, 1), LocalDate.of(2023, 1, 1));
        var newer = factor("GLOBAL", new BigDecimal("0.72"), LocalDate.of(2023, 1, 1), null);
        Mockito.when(repository.findCandidates(electricity.getId(), ImpactType.CO2E))
                .thenReturn(List.of(newer, older));

        var y2020 = resolver.resolve(electricity, ImpactType.CO2E, "GLOBAL", LocalDate.of(2020, 6, 1));
        var y2026 = resolver.resolve(electricity, ImpactType.CO2E, "GLOBAL", LocalDate.of(2026, 6, 1));

        assertThat(y2020).get().extracting(EmissionFactor::getValue).isEqualTo(new BigDecimal("0.90"));
        assertThat(y2026).get().extracting(EmissionFactor::getValue).isEqualTo(new BigDecimal("0.72"));
    }

    @Test
    void emptyWhenNoFactorCoversThePeriod() {
        var future = factor("GLOBAL", new BigDecimal("0.5"), LocalDate.of(2030, 1, 1), null);
        Mockito.when(repository.findCandidates(electricity.getId(), ImpactType.CO2E))
                .thenReturn(List.of(future));

        assertThat(resolver.resolve(electricity, ImpactType.CO2E, "GLOBAL", LocalDate.of(2026, 1, 1)))
                .isEmpty();
    }

    @Test
    void emptyWhenNoCandidatesAtAll() {
        Mockito.when(repository.findCandidates(electricity.getId(), ImpactType.WATER))
                .thenReturn(List.of());
        assertThat(resolver.resolve(electricity, ImpactType.WATER, "GLOBAL", LocalDate.of(2026, 1, 1)))
                .isEmpty();
    }
}
