package com.ieia.calculation;

import com.ieia.activity.ActivityCategory;
import com.ieia.activity.ActivityType;
import com.ieia.emissionfactor.EmissionFactor;
import com.ieia.emissionfactor.EmissionFactorRepository;
import com.ieia.emissionfactor.FactorResolver;
import com.ieia.emissionfactor.ImpactType;
import com.ieia.record.EnvironmentalRecord;
import com.ieia.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ImpactCalculationEngineTest {

    private EmissionFactorRepository factorRepository;
    private ImpactCalculationEngine engine;
    private User user;

    @BeforeEach
    void setUp() {
        factorRepository = Mockito.mock(EmissionFactorRepository.class);
        engine = new ImpactCalculationEngine(factorRepository, new FactorResolver(factorRepository), new UnitConverter());
        user = new User();
        user.setId(UUID.randomUUID());
        user.setRegion("IN");
    }

    private ActivityType activity(String code, ActivityCategory cat) {
        ActivityType at = new ActivityType();
        at.setId(UUID.randomUUID());
        at.setCode(code);
        at.setCategory(cat);
        return at;
    }

    private EmissionFactor factor(ActivityType at, ImpactType type, String from, String to, String value, String region) {
        EmissionFactor f = new EmissionFactor();
        f.setId(UUID.randomUUID());
        f.setActivityType(at);
        f.setImpactType(type);
        f.setUnitFrom(from);
        f.setUnitTo(to);
        f.setValue(new BigDecimal(value));
        f.setRegion(region);
        f.setSource("test");
        f.setEffectiveFrom(LocalDate.of(2000, 1, 1));
        f.setActive(true);
        return f;
    }

    private EnvironmentalRecord record(ActivityType at, String amount, String unit) {
        EnvironmentalRecord r = new EnvironmentalRecord();
        r.setId(UUID.randomUUID());
        r.setUser(user);
        r.setActivityType(at);
        r.setPeriodMonth(LocalDate.of(2026, 5, 1));
        r.setAmount(new BigDecimal(amount));
        r.setUnit(unit);
        return r;
    }

    @Test
    void electricity_produces_co2e_and_energy_using_regional_factor() {
        ActivityType elec = activity("electricity_grid", ActivityCategory.ELECTRICITY);
        var co2eIn = factor(elec, ImpactType.CO2E, "kWh", "kgCO2e", "0.716", "IN");
        var co2eGlobal = factor(elec, ImpactType.CO2E, "kWh", "kgCO2e", "0.475", "GLOBAL");
        var energy = factor(elec, ImpactType.ENERGY, "kWh", "MJ", "3.6", "GLOBAL");
        Mockito.when(factorRepository.findActiveByActivityType(elec.getId()))
                .thenReturn(List.of(co2eIn, co2eGlobal, energy));
        Mockito.when(factorRepository.findCandidates(elec.getId(), ImpactType.CO2E))
                .thenReturn(List.of(co2eIn, co2eGlobal));
        Mockito.when(factorRepository.findCandidates(elec.getId(), ImpactType.ENERGY))
                .thenReturn(List.of(energy));

        var result = engine.calculate(record(elec, "250", "kWh"));

        assertThat(result.warnings()).isEmpty();
        var totals = ImpactCalculationEngine.totals(result.impacts());
        assertThat(totals.get(ImpactType.CO2E)).isEqualByComparingTo("179.0000");  // 250 * 0.716 (IN wins)
        assertThat(totals.get(ImpactType.ENERGY)).isEqualByComparingTo("900.0000"); // 250 * 3.6
    }

    @Test
    void petrol_litres_convert_correctly() {
        ActivityType petrol = activity("petrol", ActivityCategory.FUEL);
        var co2e = factor(petrol, ImpactType.CO2E, "litre", "kgCO2e", "2.31", "GLOBAL");
        var energy = factor(petrol, ImpactType.ENERGY, "litre", "MJ", "34.2", "GLOBAL");
        Mockito.when(factorRepository.findActiveByActivityType(petrol.getId())).thenReturn(List.of(co2e, energy));
        Mockito.when(factorRepository.findCandidates(petrol.getId(), ImpactType.CO2E)).thenReturn(List.of(co2e));
        Mockito.when(factorRepository.findCandidates(petrol.getId(), ImpactType.ENERGY)).thenReturn(List.of(energy));

        var totals = ImpactCalculationEngine.totals(engine.calculate(record(petrol, "40", "litre")).impacts());

        assertThat(totals.get(ImpactType.CO2E)).isEqualByComparingTo("92.4000");
        assertThat(totals.get(ImpactType.ENERGY)).isEqualByComparingTo("1368.0000");
    }

    @Test
    void mwh_amount_is_converted_to_kwh_before_applying_factor() {
        ActivityType elec = activity("electricity_grid", ActivityCategory.ELECTRICITY);
        var co2e = factor(elec, ImpactType.CO2E, "kWh", "kgCO2e", "0.5", "GLOBAL");
        Mockito.when(factorRepository.findActiveByActivityType(elec.getId())).thenReturn(List.of(co2e));
        Mockito.when(factorRepository.findCandidates(elec.getId(), ImpactType.CO2E)).thenReturn(List.of(co2e));

        var totals = ImpactCalculationEngine.totals(engine.calculate(record(elec, "1.2", "MWh")).impacts());

        assertThat(totals.get(ImpactType.CO2E)).isEqualByComparingTo("600.0000"); // 1200 kWh * 0.5
    }

    @Test
    void recycled_waste_has_zero_landfilled_mass() {
        ActivityType recycled = activity("waste_recycled", ActivityCategory.WASTE);
        var co2e = factor(recycled, ImpactType.CO2E, "kg", "kgCO2e", "0.021", "GLOBAL");
        var waste = factor(recycled, ImpactType.WASTE, "kg", "kg", "0", "GLOBAL");
        Mockito.when(factorRepository.findActiveByActivityType(recycled.getId())).thenReturn(List.of(co2e, waste));
        Mockito.when(factorRepository.findCandidates(recycled.getId(), ImpactType.CO2E)).thenReturn(List.of(co2e));
        Mockito.when(factorRepository.findCandidates(recycled.getId(), ImpactType.WASTE)).thenReturn(List.of(waste));

        var totals = ImpactCalculationEngine.totals(engine.calculate(record(recycled, "12", "kg")).impacts());

        assertThat(totals.get(ImpactType.WASTE)).isEqualByComparingTo("0.0000");
        assertThat(totals.get(ImpactType.CO2E)).isEqualByComparingTo("0.2520");
    }

    @Test
    void missing_factor_for_a_dimension_is_reported_as_warning_not_an_error() {
        ActivityType elec = activity("electricity_grid", ActivityCategory.ELECTRICITY);
        var co2e = factor(elec, ImpactType.CO2E, "kWh", "kgCO2e", "0.5", "GLOBAL");
        // ENERGY is advertised as a dimension but no candidate covers the period
        var energyFuture = factor(elec, ImpactType.ENERGY, "kWh", "MJ", "3.6", "GLOBAL");
        energyFuture.setEffectiveFrom(LocalDate.of(2040, 1, 1));
        Mockito.when(factorRepository.findActiveByActivityType(elec.getId())).thenReturn(List.of(co2e, energyFuture));
        Mockito.when(factorRepository.findCandidates(elec.getId(), ImpactType.CO2E)).thenReturn(List.of(co2e));
        Mockito.when(factorRepository.findCandidates(elec.getId(), ImpactType.ENERGY)).thenReturn(List.of(energyFuture));

        var result = engine.calculate(record(elec, "100", "kWh"));

        assertThat(result.warnings()).containsExactly("NO_FACTOR:ENERGY");
        assertThat(ImpactCalculationEngine.totals(result.impacts())).containsOnlyKeys(ImpactType.CO2E);
    }
}
