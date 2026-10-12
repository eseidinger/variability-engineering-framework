package de.eseidinger.variabilityengineering.energy;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyFeatureModelTest {
    private final EnergyFeatureModel model = new EnergyFeatureModel();

    @Test
    void includesTheElectricalAndRoofBoundaryWithTheSharedTolerance() {
        var catalog = catalog(List.of(pv("pv-one", 0.4, 2), inverter("inv-one", 1), battery("bat-one", true)));
        var atMinimumRatio = model.generate(request(4 - 0.5e-9, List.of()), catalog);
        assertEquals(5, atMinimumRatio.size());
        assertTrue(atMinimumRatio.stream().allMatch(configuration -> configuration.pvQuantity() == 2));
        assertTrue(model.generate(request(4 - 2e-9, List.of()), catalog).isEmpty());

        var maxCatalog = catalog(List.of(pv("pv-one", 0.5, 1), inverter("inv-one", 1), battery("bat-one", true)));
        assertTrue(model.generate(request(3, List.of()), maxCatalog).stream().anyMatch(c -> c.dcAcRatio() == 1.5));
        assertTrue(model.generate(request(4, List.of()), maxCatalog).stream().noneMatch(c -> c.pvQuantity() == 4));
    }

    @Test
    void unavailableModelsAreExcludedAndNoBatteryIsStillAChoice() {
        var unavailablePv = new EnergyComponent.PvModule(metadata("pv-unavailable", false), 0.4, 2);
        var unavailableInverter = new EnergyComponent.Inverter(metadata("inv-unavailable", false), 1, 0.97);
        var catalog = catalog(List.of(pv("pv-one", 0.4, 2), unavailablePv,
                inverter("inv-one", 1), unavailableInverter, battery("bat-one", false)));
        var generated = model.generate(request(4, List.of()), catalog);
        assertEquals(1, generated.size());
        assertEquals("pv-one-q2-inv-one-battery-none", generated.getFirst().id());
        assertEquals(2, generated.getFirst().selections().size());
    }

    @Test
    void orderingAndIdentityAreIndependentOfCatalogInputOrder() {
        var input = new ArrayList<EnergyComponent>(List.of(pv("pv-zed", 0.4, 2), pv("pv-aaa", 0.4, 2),
                inverter("inv-one", 1), battery("bat-one", true)));
        var original = catalog(input);
        Collections.reverse(input);
        var reordered = catalog(input);
        input.clear();
        var first = model.generate(request(6, List.of()), original);
        assertEquals(first, model.generate(request(6, List.of()), reordered));
        assertEquals(first.stream().map(EnergyConfiguration::id).sorted().toList(), first.stream().map(EnergyConfiguration::id).toList());
        assertNotEquals(first.get(0).id(), first.get(1).id());
        assertThrows(UnsupportedOperationException.class, () -> original.components().clear());
    }

    @Test
    void validatesTheBatterySelectionAsACompleteAssignment() {
        var pv = pv("pv-one", 0.4, 2);
        var inverter = inverter("inv-one", 1);
        assertThrows(IllegalArgumentException.class, () -> new EnergyConfiguration(pv, 0, inverter, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new EnergyConfiguration(pv, 101, inverter, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new EnergyConfiguration(pv, 2, inverter, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new EnergyConfiguration(pv, 2, inverter, battery("bat-one", true), 0));
        assertThrows(IllegalArgumentException.class, () -> new EnergyConfiguration(pv, 2, inverter, battery("bat-one", true), 5));
    }

    @Test
    void estimatesBeforeMaterializingAndRejectsAnOversizedSearch() {
        var components = new ArrayList<EnergyComponent>();
        IntStream.range(0, 20).forEach(i -> {
            components.add(pv("pv-model-" + i, 1, 1));
            components.add(inverter("inv-model-" + i, 80));
            components.add(battery("bat-model-" + i, true));
        });
        var catalog = catalog(components);
        var request = request(Double.MAX_VALUE, List.of());
        // PV quantities 64..100 pass the ratio: 37 * 20 * 20 * (1 + 4 * 20).
        assertEquals(1_198_800, model.estimateCandidateEvaluations(request, catalog));
        var failure = assertThrows(EnergyModelException.class, () -> model.generate(request, catalog));
        assertEquals("search_space_limit_exceeded", failure.code());
    }

    @Test
    void permitsTheExactSearchLimit() {
        var components = new ArrayList<EnergyComponent>();
        IntStream.range(0, 20).forEach(i -> {
            components.add(pv("pv-model-" + i, 1, 1));
            components.add(inverter("inv-model-" + i, 25));
        });
        IntStream.range(0, 6).forEach(i -> components.add(battery("bat-model-" + i, true)));
        // PV quantities 20..29 pass roof and ratio: 10 * 20 * 20 * 25.
        var catalog = catalog(components);
        var request = request(29, List.of());
        assertEquals(100_000, model.estimateCandidateEvaluations(request, catalog));
        assertEquals(100_000, model.generate(request, catalog).size());
        assertEquals(110_000, model.estimateCandidateEvaluations(request(30, List.of()), catalog));
        assertThrows(EnergyModelException.class, () -> model.generate(request(30, List.of()), catalog));
    }

    @Test
    void neverIgnoresExistingEquipment() {
        var request = request(4, List.of("pv-one"));
        var catalog = catalog(List.of(pv("pv-one", 0.4, 2), inverter("inv-one", 1), battery("bat-one", true)));
        var failure = assertThrows(EnergyModelException.class, () -> model.estimateCandidateEvaluations(request, catalog));
        assertEquals("existing_equipment_not_supported", failure.code());
        assertThrows(EnergyModelException.class, () -> model.generate(request, catalog));
    }

    @Test
    void respectsPvCapAndEmptyRoofWithoutIntegerOverflow() {
        var catalog = catalog(List.of(pv("pv-one", 1, 1), inverter("inv-one", 80), battery("bat-one", false)));
        assertTrue(model.generate(request(0, List.of()), catalog).isEmpty());
        var candidates = model.generate(request(Double.MAX_VALUE, List.of()), catalog);
        assertEquals(37, candidates.size());
        assertEquals(100, candidates.stream().mapToInt(EnergyConfiguration::pvQuantity).max().orElseThrow());
    }

    @Test
    void rejectsCatalogDuplicatesCountsCurrenciesAndVersionChanges() {
        var base = List.<EnergyComponent>of(pv("pv-one", 0.4, 2), inverter("inv-one", 1), battery("bat-one", true));
        var duplicated = new ArrayList<>(base);
        duplicated.add(base.getFirst());
        assertThrows(IllegalArgumentException.class, () -> catalog(duplicated));
        var tooMany = new ArrayList<>(base);
        IntStream.range(0, 20).forEach(i -> tooMany.add(pv("pv-extra-" + i, 0.4, 2)));
        assertThrows(IllegalArgumentException.class, () -> catalog(tooMany));
        assertThrows(IllegalArgumentException.class, () -> catalog(base.subList(0, 2)));
        assertThrows(IllegalArgumentException.class, () -> new EnergyCatalog(EnergyCatalog.SCHEMA_VERSION, "1.0.0", "USD", base));
        assertThrows(IllegalArgumentException.class, () -> new EnergyCatalog("energy-component-catalog/v2", "1.0.0", "EUR", base));
        assertThrows(IllegalArgumentException.class, () -> new EnergyCatalog(EnergyCatalog.SCHEMA_VERSION, "latest", "EUR", base));
    }

    @Test
    void rejectsAmbiguousOrOversizedConfigurationIdsRatherThanMergingChoices() {
        var ambiguous = catalog(List.of(pv("pv-aaa", 1, 1), pv("pv-aaa-q1-inv-xxx", 0.5, 1),
                inverter("inv-xxx-q2-inv-yyy", 1), inverter("inv-yyy", 1), battery("bat-one", false)));
        var collision = assertThrows(EnergyModelException.class, () -> model.generate(request(2, List.of()), ambiguous));
        assertEquals("configuration_id_collision", collision.code());
        var longIds = catalog(List.of(pv("pv-" + "a".repeat(97), 1, 1),
                inverter("inv-" + "b".repeat(96), 1), battery("bat-one", false)));
        var length = assertThrows(EnergyModelException.class, () -> model.generate(request(1, List.of()), longIds));
        assertEquals("configuration_id_limit_exceeded", length.code());
    }

    private static EnergyCatalog catalog(List<EnergyComponent> components) {
        return new EnergyCatalog(EnergyCatalog.SCHEMA_VERSION, "1.0.0", "EUR", components);
    }

    static EnergyOptimizationRequest request(double roofArea, List<String> existingIds) {
        return new EnergyOptimizationRequest(EnergyOptimizationRequest.SCHEMA_VERSION,
                new EnergyOptimizationRequest.DatasetReference("energy-test-reference", "1.0.0", EnergyOptimizationRequest.PROFILE),
                new EnergyOptimizationRequest.Site(EnergyOptimizationRequest.REGION, roofArea, 4500, existingIds),
                new EnergyOptimizationRequest.Constraints(18000, 0.55), "MIN_ANNUALIZED_COST",
                new EnergyOptimizationRequest.Assumptions(20, 0.04));
    }

    static EnergyComponent.Metadata metadata(String id, boolean available) {
        return new EnergyComponent.Metadata(id, "Test model", 100, available, new EnergyComponent.Source(
                "Test fixture", URI.create("urn:vef:test-source"), "1.0.0", "Apache-2.0", Instant.parse("2026-10-12T00:00:00Z")));
    }

    static EnergyComponent.PvModule pv(String id, double capacity, double area) {
        return new EnergyComponent.PvModule(metadata(id, true), capacity, area);
    }

    static EnergyComponent.Inverter inverter(String id, double power) {
        return new EnergyComponent.Inverter(metadata(id, true), power, 0.97);
    }

    static EnergyComponent.Battery battery(String id, boolean available) {
        return new EnergyComponent.Battery(metadata(id, available), 5, 2.5, 2.5, 0.9);
    }
}
