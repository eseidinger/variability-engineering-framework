package de.eseidinger.variabilityengineering.energy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnergyOptimizerConformanceTest {
    private final EnergyOptimizer optimizer = new EnergyOptimizer();

    @Test
    void reproducesEveryReleasedCandidateAndTheCompleteRanking() throws IOException {
        var reference = EnergyFixture.document("expected-results/optimization-reference.json");
        var publication = EnergyFixture.publication();
        var result = optimizer.optimize(EnergyFixture.request(), publication.catalog(), publication.profile());
        assertEquals(252, result.candidates().size());
        assertEquals(reference.path("result").path("feasibleCandidateCount").asInt(), result.feasibleCandidateCount());
        assertEquals(reference.path("result").path("status").asText(), result.status());
        assertEquals(reference.path("result").path("optimalityStatus").asText(), result.optimalityStatus());
        assertEquals(reference.path("result").path("recommendedConfigurationId").asText(),
                result.recommendedConfigurationId());
        var ranking = new ArrayList<String>();
        reference.path("ranking").forEach(id -> ranking.add(id.asText()));
        assertEquals(ranking, result.ranking());
        for (int i = 0; i < result.candidates().size(); i++) {
            var candidate = result.candidates().get(i);
            var oracle = reference.path("candidates").get(i);
            assertEquals(oracle.path("configurationId").asText(), candidate.configuration().id());
            assertEquals(oracle.path("feasible").asBoolean(), candidate.feasible(), candidate.configuration().id());
            for (int j = 0; j < candidate.constraints().size(); j++) {
                var constraint = candidate.constraints().get(j);
                var expected = oracle.path("constraints").get(j);
                assertEquals(expected.path("code").asText(), constraint.code());
                assertEquals(expected.path("status").asText(), constraint.status(), candidate.configuration().id());
                assertEquals(expected.path("actual").asDouble(), ((Number) constraint.actual()).doubleValue(), 1e-9);
            }
            var metrics = candidate.metrics();
            var expected = oracle.path("metrics");
            assertEquals(expected.path("initialInvestmentEUR").asDouble(), metrics.initialInvestmentEUR(), 1e-9);
            assertEquals(expected.path("annualizedCostEUR").asDouble(), metrics.annualizedCostEUR(), 1e-9, candidate.configuration().id());
            assertEquals(expected.path("selfSufficiency").asDouble(), metrics.selfSufficiency(), 1e-9);
            assertEquals(expected.path("annualDemandKWh").asDouble(), metrics.annualDemandKWh(), 1e-6);
            assertEquals(expected.path("gridImportKWh").asDouble(), metrics.gridImportKWh(), 1e-6);
            assertEquals(expected.path("gridExportKWh").asDouble(), metrics.gridExportKWh(), 1e-6);
            assertEquals(expected.path("onSiteSolarUseKWh").asDouble(), metrics.onSiteSolarUseKWh(), 1e-6);
            assertEquals(expected.path("unmetDemandKWh").asDouble(), metrics.unmetDemandKWh(), 1e-6);
            assertEquals(expected.path("inverterClippingKWh").asDouble(), metrics.inverterClippingKWh(), 1e-6);
            assertEquals(expected.path("terminalDiscardedEnergyKWh").asDouble(),
                    metrics.terminalDiscardedEnergyKWh(), 1e-6);
        }
    }

    @Test
    void impossibleConstraintsYieldAnExhaustiveInfeasibleResult() throws IOException {
        var reference = EnergyFixture.request();
        var impossible = new EnergyOptimizationRequest(reference.schemaVersion(), reference.dataset(), reference.site(),
                new EnergyOptimizationRequest.Constraints(0, 1), reference.objective(), reference.assumptions());
        var publication = EnergyFixture.publication();
        var result = optimizer.optimize(impossible, publication.catalog(), publication.profile());
        assertEquals(252, result.candidates().size());
        assertFalse(result.candidates().stream().anyMatch(EnergyOptimizer.Candidate::feasible));
        assertEquals("INFEASIBLE", result.status());
        assertEquals("NOT_APPLICABLE", result.optimalityStatus());
        assertNull(result.recommendedConfigurationId());
    }
}
