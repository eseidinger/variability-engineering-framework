package de.eseidinger.variabilityengineering.energy;

import de.eseidinger.variabilityengineering.core.configuration.ElementSelection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyFeatureModelConformanceTest {
    private final EnergyFeatureModel model = new EnergyFeatureModel();

    @Test
    void reproducesEveryReleasedConfigurationAndItsQuantities() throws IOException {
        var catalog = EnergyFixture.catalog();
        var request = EnergyFixture.request();
        var expected = EnergyFixture.document("expected-results/optimization-reference.json");
        var actual = model.generate(request, catalog);

        assertEquals(252, actual.size());
        assertEquals(expected.path("result").path("evaluatedCandidateCount").asLong(),
                model.estimateCandidateEvaluations(request, catalog));
        var expectedIds = new ArrayList<String>();
        expected.path("candidates").forEach(candidate -> expectedIds.add(candidate.path("configurationId").asText()));
        assertEquals(expectedIds, actual.stream().map(EnergyConfiguration::id).toList());
        for (int i = 0; i < actual.size(); i++) {
            var candidate = actual.get(i);
            var oracle = expected.path("candidates").get(i);
            var selections = new ArrayList<ElementSelection>();
            oracle.path("components").forEach(component -> selections.add(new ElementSelection(
                    component.path("componentId").asText(), component.path("quantity").asInt(),
                    component.path("existing").asBoolean())));
            assertEquals(selections, candidate.selections(), candidate.id());
            assertEquals(oracle.path("metrics").path("initialInvestmentEUR").asDouble(),
                    candidate.initialInvestmentEUR(), 1e-9, candidate.id());
            assertEquals(oracle.path("constraints").get(0).path("actual").asDouble(),
                    candidate.usedRoofAreaM2(), 1e-6, candidate.id());
            assertEquals(oracle.path("constraints").get(1).path("actual").asDouble(),
                    candidate.dcAcRatio(), 1e-9, candidate.id());
        }
        assertThrows(UnsupportedOperationException.class, () -> actual.clear());
    }

    @Test
    void keepsInvestmentAndSelfSufficiencyForLaterEvaluation() throws IOException {
        var reference = EnergyFixture.request();
        var impossible = new EnergyOptimizationRequest(reference.schemaVersion(), reference.dataset(), reference.site(),
                new EnergyOptimizationRequest.Constraints(0, 1), reference.objective(), reference.assumptions());
        assertEquals(model.generate(reference, EnergyFixture.catalog()), model.generate(impossible, EnergyFixture.catalog()));
        assertFalse(model.generate(impossible, EnergyFixture.catalog()).isEmpty());
    }

    @Test
    void matchesTheLockedModelVersion() throws IOException {
        var root = java.nio.file.Path.of("../../../contracts/energy/contract-kit.lock.json");
        var lock = new com.fasterxml.jackson.databind.ObjectMapper().readTree(root.toFile());
        assertEquals(lock.path("referenceVersions").path("model").asText(), EnergyFeatureModel.VERSION);
    }
}
