package de.eseidinger.variabilityengineering.energy;

import de.eseidinger.variabilityengineering.core.configuration.ElementSelection;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static de.eseidinger.variabilityengineering.energy.EnergyFeatureModelTest.metadata;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyInputValidationTest {
    @Test
    void rejectsNonFiniteNegativeAndZeroTechnicalValues() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0}) {
            assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.PvModule(metadata("pv-one", true), invalid, 1));
            assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.PvModule(metadata("pv-one", true), 1, invalid));
            assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.Inverter(metadata("inv-one", true), invalid, 1));
            assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.Inverter(metadata("inv-one", true), 1, invalid));
            assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.Battery(metadata("bat-one", true), invalid, 1, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.Battery(metadata("bat-one", true), 1, invalid, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.Battery(metadata("bat-one", true), 1, 1, invalid, 1));
            assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.Battery(metadata("bat-one", true), 1, 1, 1, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.Inverter(metadata("inv-one", true), 1, 1.01));
        assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.Battery(metadata("bat-one", true), 1, 1, 1, 1.01));
    }

    @Test
    void rejectsInvalidPricesAndPreservesOptionalSourceVersion() {
        var valid = metadata("pv-one", true);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.Metadata("pv-one", "Model", invalid, true, valid.source()));
        }
        var zeroPrice = new EnergyComponent.Metadata("pv-one", "Model", 0, true, valid.source());
        assertEquals(0, zeroPrice.unitPriceEUR());
        new EnergyComponent.Source("Fixture", URI.create("urn:fixture:source"), null, "CC-BY-4.0", Instant.EPOCH);
        assertThrows(IllegalArgumentException.class, () -> new EnergyComponent.Source(
                "Fixture", URI.create("relative-source"), null, "CC-BY-4.0", Instant.EPOCH));
    }

    @Test
    void validatesCustomerBoundsAndProtectsRequestCollections() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.Site(EnergyOptimizationRequest.REGION, invalid, 1, List.of()));
            assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.Site(EnergyOptimizationRequest.REGION, 1, invalid, List.of()));
            assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.Constraints(invalid, 0.5));
            assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.Constraints(1, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.Site(EnergyOptimizationRequest.REGION, 1, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.Constraints(1, 1.01));
        assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.Site("DE-BE", 1, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.Site(EnergyOptimizationRequest.REGION, 1, 1, List.of("pv-one", "pv-one")));
        assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.Assumptions(10, 0.04));
        assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.Assumptions(20, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.DatasetReference("energy-test", "latest", EnergyOptimizationRequest.PROFILE));
        assertThrows(IllegalArgumentException.class, () -> new EnergyOptimizationRequest.DatasetReference("energy-test", "1.0.0", "urn:portfolio:energy-configuration-input:v2"));
        var original = new ArrayList<>(List.of("pv-one"));
        var site = new EnergyOptimizationRequest.Site(EnergyOptimizationRequest.REGION, 1, 1, original);
        original.clear();
        assertEquals(List.of("pv-one"), site.existingComponentIds());
        assertThrows(UnsupportedOperationException.class, () -> site.existingComponentIds().clear());
    }

    @Test
    void quantitativeSelectionsDistinguishQuantityAndOwnership() {
        assertNotEquals(new ElementSelection("module", 1, false), new ElementSelection("module", 2, false));
        assertNotEquals(new ElementSelection("module", 1, false), new ElementSelection("module", 1, true));
        assertThrows(IllegalArgumentException.class, () -> new ElementSelection("module", 0, false));
        assertThrows(IllegalArgumentException.class, () -> new ElementSelection("", 1, false));
    }

    @Test
    void rejectsNonFiniteAggregateCapacityAndInvestment() {
        var inverter = EnergyFeatureModelTest.inverter("inv-one", 1);
        var extremeCapacity = EnergyFeatureModelTest.pv("pv-one", Double.MAX_VALUE, 1);
        assertThrows(IllegalArgumentException.class, () -> new EnergyConfiguration(extremeCapacity, 2, inverter, null, 0));
        var expensive = new EnergyComponent.PvModule(new EnergyComponent.Metadata("pv-one", "Expensive", Double.MAX_VALUE,
                true, metadata("pv-one", true).source()), 1, 1);
        assertThrows(IllegalArgumentException.class, () -> new EnergyConfiguration(expensive, 2, inverter, null, 0));
        var tinyInverter = EnergyFeatureModelTest.inverter("inv-tiny", Double.MIN_VALUE);
        assertThrows(IllegalArgumentException.class, () -> new EnergyConfiguration(
                EnergyFeatureModelTest.pv("pv-one", 1, 1), 1, tinyInverter, null, 0));
    }
}
