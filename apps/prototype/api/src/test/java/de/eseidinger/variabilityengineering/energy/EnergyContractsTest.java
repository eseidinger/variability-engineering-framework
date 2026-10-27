package de.eseidinger.variabilityengineering.energy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyContractsTest {
    @Test
    void packagedSchemasMatchEveryByteOfThePinnedRelease() throws Exception {
        try (var index = EnergyContracts.class.getResourceAsStream("/energy-contract/index.json")) {
            for (var name : EnergyContracts.parse(index.readAllBytes())) {
                try (var resource = EnergyContracts.class.getResourceAsStream("/energy-contract/" + name.asText())) {
                    assertArrayEquals(Files.readAllBytes(EnergyFixture.kitPath("schemas/" + name.asText())),
                            resource.readAllBytes());
                }
            }
        }
    }

    @Test
    void rejectsOptionalProvenanceTypesLengthsFormatsAndUnknownFields() throws Exception {
        for (String mutation : Arrays.asList("type", "length", "timestamp", "unknown")) {
            ObjectNode manifest = (ObjectNode) EnergyContracts.parse(Files.readAllBytes(
                    EnergyFixture.kitPath("fixtures/energy-reference-wasserburg-am-inn-2024/manifest.json")));
            ObjectNode source = (ObjectNode) manifest.path("provenance").get(0);
            switch (mutation) {
                case "type" -> source.put("sourceVersion", 123);
                case "length" -> source.put("rawArtifactId", "x".repeat(101));
                case "timestamp" -> source.put("retrievedAt", "2026-02-30T00:00:00Z");
                case "unknown" -> source.put("secret", "not allowed");
                default -> throw new AssertionError();
            }
            assertThrows(IllegalArgumentException.class,
                    () -> EnergyContracts.validate("dataset-publication-v1", manifest), mutation);
        }
    }

    @Test
    void rejectsInvalidCandidateMetricsBeforeEvidenceCanReachTheModel() throws Exception {
        var candidate = (ObjectNode) EnergyContracts.parse(Files.readAllBytes(EnergyFixture.kitPath("examples/candidate.json")));
        ((ObjectNode) candidate.path("metrics")).put("selfSufficiency", 1.1);
        assertThrows(IllegalArgumentException.class, () -> EnergyContracts.validate("energy-candidate-v1", candidate));
    }

    @Test
    void rejectsDuplicateKeysAndTrailingDocuments() {
        assertThrows(java.io.IOException.class, () -> EnergyContracts.parse("{\"a\":1,\"a\":2}"));
        assertThrows(java.io.IOException.class, () -> EnergyContracts.parse("{} {}"));
    }
}
