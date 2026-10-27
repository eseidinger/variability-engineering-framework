package de.eseidinger.variabilityengineering.energy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Verifies bytes exported after the independent ODIP publication/restart/restore drill. */
@EnabledIfEnvironmentVariable(named = "ENERGY_CORRECTED_FIXTURE_DIRECTORY", matches = ".+")
class EnergyCorrectedPublicationTest {
    @Test
    void importsRestoredCorrectedVersionAndMatchesIndependentPythonOracleWithoutChangingOriginal() throws Exception {
        Path root = Path.of(System.getenv("ENERGY_CORRECTED_FIXTURE_DIRECTORY"));
        var mapper = new ObjectMapper();
        var original = EnergyFixture.publication();
        var optimizer = new EnergyOptimizer();
        var before = optimizer.optimize(EnergyFixture.request(), original.catalog(), original.profile());
        byte[] manifest = Files.readAllBytes(root.resolve("manifest.json"));
        var artifacts = new HashMap<String, byte[]>();
        for (var descriptor : EnergyContracts.parse(manifest).path("artifacts")) {
            String id = descriptor.path("artifactId").asText();
            artifacts.put(id, Files.readAllBytes(root.resolve("artifacts/" + id + ".json")));
        }
        var corrected = new EnergyPublicationVerifier().verify(manifest, artifacts);
        var request = mapper.readValue(root.resolve("request.json").toFile(), EnergyOptimizationRequest.class);
        var actual = optimizer.optimize(request, corrected.catalog(), corrected.profile());
        var expected = EnergyContracts.parse(Files.readAllBytes(root.resolve("expected.json")));
        assertEquals("1.0.1", corrected.profile().version());
        assertNotEquals(original.manifestSha256(), corrected.manifestSha256());
        assertEquals(expected.path("result").path("recommendedConfigurationId").asText(), actual.recommendedConfigurationId());
        assertEquals(expected.path("result").path("feasibleCandidateCount").asInt(), actual.feasibleCandidateCount());
        var ranking = new ArrayList<String>();
        expected.path("ranking").forEach(node -> ranking.add(node.asText()));
        assertEquals(ranking, actual.ranking());
        assertEquals(expected.path("candidates").size(), actual.candidates().size());
        for (int i = 0; i < actual.candidates().size(); i++) {
            var candidate = actual.candidates().get(i);
            var oracle = expected.path("candidates").get(i);
            assertEquals(oracle.path("configurationId").asText(), candidate.configuration().id());
            assertEquals(oracle.path("feasible").asBoolean(), candidate.feasible());
            JsonNode metrics = mapper.valueToTree(candidate.metrics());
            for (var field : oracle.path("metrics").properties()) {
                assertEquals(field.getValue().asDouble(), metrics.path(field.getKey()).asDouble(),
                        field.getKey().endsWith("EUR") ? 0.0 : field.getKey().endsWith("KWh") ? 1e-6 : 1e-9,
                        candidate.configuration().id() + "/" + field.getKey());
            }
        }
        assertNotEquals(before.candidates().getFirst().metrics().annualizedCostEUR(),
                actual.candidates().getFirst().metrics().annualizedCostEUR());
        var after = optimizer.optimize(EnergyFixture.request(), original.catalog(), original.profile());
        assertEquals(before, after);
    }
}
