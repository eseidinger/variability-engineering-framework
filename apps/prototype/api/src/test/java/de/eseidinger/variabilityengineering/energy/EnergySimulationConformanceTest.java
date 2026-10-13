package de.eseidinger.variabilityengineering.energy;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergySimulationConformanceTest {
    private final EnergySimulation simulation = new EnergySimulation();

    @Test
    void reproducesEveryReleasedHourlyCase() throws IOException {
        for (var testCase : EnergyFixture.document("expected-results/simulation-cases.json")) {
            var inputs = testCase.path("inputs");
            var batteryNode = inputs.path("battery");
            var battery = batteryNode.isNull() ? null : new EnergySimulation.Battery(
                    batteryNode.path("quantity").asInt(1), batteryNode.path("usableCapacityKWh").asDouble(),
                    batteryNode.path("maxChargePowerKW").asDouble(), batteryNode.path("maxDischargePowerKW").asDouble(),
                    batteryNode.path("roundTripEfficiencyFraction").asDouble());
            var hours = new ArrayList<EnergySimulation.HourInput>();
            for (var hour : inputs.path("hours")) {
                hours.add(new EnergySimulation.HourInput(Instant.parse(hour.path("start").asText()),
                        hour.path("pvYieldPerKWp").asDouble(), hour.path("loadKWh").asDouble(),
                        hour.has("importTariffEURPerKWh") ? hour.path("importTariffEURPerKWh").asDouble() : null,
                        hour.has("exportTariffEURPerKWh") ? hour.path("exportTariffEURPerKWh").asDouble() : null));
            }
            var result = simulation.simulate(new EnergySimulation.Input(inputs.path("pvCapacityKWp").asDouble(),
                    inputs.path("inverterEfficiencyFraction").asDouble(), inputs.path("inverterAcPowerKW").asDouble(),
                    battery, hours), true);
            var expected = testCase.path("expected");
            assertEquals(expected.path("hours").size(), result.hours().size(), testCase.path("caseId").asText());
            for (int i = 0; i < result.hours().size(); i++) {
                var actual = result.hours().get(i);
                var oracle = expected.path("hours").get(i);
                assertEquals(Instant.parse(oracle.path("start").asText()), actual.start());
                assertEnergy(oracle, "pvDcKWh", actual.pvDcKWh());
                assertEnergy(oracle, "pvAcKWh", actual.pvAcKWh());
                assertEnergy(oracle, "loadKWh", actual.loadKWh());
                assertEnergy(oracle, "directSolarKWh", actual.directSolarKWh());
                assertEnergy(oracle, "batteryChargeAcKWh", actual.batteryChargeAcKWh());
                assertEnergy(oracle, "batteryDischargeAcKWh", actual.batteryDischargeAcKWh());
                assertEnergy(oracle, "stateOfChargeKWh", actual.stateOfChargeKWh());
                assertEnergy(oracle, "gridImportKWh", actual.gridImportKWh());
                assertEnergy(oracle, "gridExportKWh", actual.gridExportKWh());
                assertEnergy(oracle, "inverterClippingKWh", actual.inverterClippingKWh());
            }
            var totals = result.totals();
            var oracle = expected.path("totals");
            assertEnergy(oracle, "pvDcKWh", totals.pvDcKWh());
            assertEnergy(oracle, "pvAcKWh", totals.pvAcKWh());
            assertEnergy(oracle, "annualDemandKWh", totals.annualDemandKWh());
            assertEnergy(oracle, "gridImportKWh", totals.gridImportKWh());
            assertEnergy(oracle, "gridExportKWh", totals.gridExportKWh());
            assertEnergy(oracle, "onSiteSolarUseKWh", totals.onSiteSolarUseKWh());
            assertEnergy(oracle, "batteryChargeAcKWh", totals.batteryChargeAcKWh());
            assertEnergy(oracle, "batteryDischargeAcKWh", totals.batteryDischargeAcKWh());
            assertEnergy(oracle, "inverterClippingKWh", totals.inverterClippingKWh());
            assertEnergy(oracle, "unmetDemandKWh", totals.unmetDemandKWh());
            assertEnergy(oracle, "terminalDiscardedEnergyKWh", totals.terminalDiscardedEnergyKWh());
            assertEnergy(oracle, "selfSufficiency", totals.selfSufficiency());
        }
    }

    @Test
    void rejectsInvalidInputAndPreservesHalfEvenRounding() {
        assertThrows(IllegalArgumentException.class, () -> new EnergySimulation.HourInput(
                Instant.EPOCH, Double.NaN, 1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new EnergySimulation.HourInput(
                Instant.EPOCH, 1, 1, .3, null));
        assertEquals(0.03, EnergyOptimizer.round(0.025, 2));
        assertEquals(0.12, EnergyOptimizer.round(0.125, 2));
        assertEquals(0.38, EnergyOptimizer.round(0.375, 2));
        assertEquals(0.05, EnergyOptimizer.capitalRecoveryFactor(0, 20));
    }

    private static void assertEnergy(JsonNode oracle, String name, double actual) {
        assertEquals(oracle.path(name).asDouble(), EnergyOptimizer.round(actual, 6), 1e-9, name);
    }
}
