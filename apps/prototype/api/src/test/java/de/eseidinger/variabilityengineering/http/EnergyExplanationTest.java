package de.eseidinger.variabilityengineering.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyRunRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyExplanationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildsApprovedSnapshotAndValidatesReleasedExplanation() throws Exception {
        var repository = new JdbcEnergyRunRepository() {
            @Override public Optional<String> recommendedCandidate(String runId, String configurationId) {
                try {
                    return Optional.of(example("candidate.json"));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
        var evidence = new EnergyEvidenceService();
        evidence.mapper = mapper;
        evidence.runs = repository;
        var run = new JdbcEnergyRunRepository.Run("run-example-0001", "prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                example("optimization-request.json"), "energy-reference-wasserburg-am-inn-2024", "1.0.0",
                "f".repeat(64), "SUCCEEDED", Instant.parse("2026-10-10T10:00:00Z"), null,
                Instant.parse("2026-10-10T10:00:01Z"), example("optimization-result.json"), null, null);
        ObjectNode sources = evidence.prepare(run);
        assertEquals(4, sources.size());
        assertEquals("CUSTOMER_INPUT", sources.path("request").path("category").asText());
        assertEquals(false, sources.path("request").path("document").has("assumptions"));

        var explanations = new ExplanationService();
        explanations.mapper = mapper;
        String valid = example("ai-explanation.json");
        assertEquals("run-example-0001", explanations.validate(valid, sources).path("runId").asText());

        String altered = valid.replace("\"value\": 1234.56", "\"value\": 1234.57");
        assertThrows(ExplanationService.InvalidExplanation.class, () -> explanations.validate(altered, sources));
        String foreign = valid.replace("\"runId\": \"run-example-0001\"", "\"runId\": \"run-foreign-0001\"");
        assertThrows(ExplanationService.InvalidExplanation.class, () -> explanations.validate(foreign, sources));
        var missingCandidate = sources.deepCopy();
        missingCandidate.remove("candidate");
        assertThrows(ExplanationService.InvalidExplanation.class, () -> explanations.validate(valid, missingCandidate));
    }

    @Test
    void validatesInfeasibleEvidenceWithoutInventingRecommendation() throws Exception {
        ObjectNode sources = (ObjectNode) mapper.readTree(Files.readString(Path.of(
                System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"), "ai/example-evidence.json")));
        sources.remove("candidate");
        ObjectNode result = (ObjectNode) sources.path("result").path("document");
        result.put("status", "INFEASIBLE");
        result.putNull("recommendedConfigurationId");
        result.put("optimalityStatus", "NOT_APPLICABLE");
        var output = mapper.createObjectNode();
        output.put("schemaVersion", "energy-ai-explanation/v1");
        output.put("runId", "run-example-0001");
        ArrayNode claims = output.putArray("claims");
        var claim = claims.addObject();
        claim.put("text", "The recorded run is infeasible; no recommendation is available.");
        claim.put("category", "CALCULATED_OUTPUT");
        ArrayNode citations = claim.putArray("citations");
        for (String pointer : new String[]{"/runId", "/dataset/datasetId", "/dataset/version",
                "/modelVersion", "/simulationVersion", "/recommendedConfigurationId",
                "/optimalityStatus", "/status"}) {
            var citation = citations.addObject();
            citation.put("source", "result");
            citation.put("pointer", pointer);
            citation.set("value", result.at(pointer));
        }
        output.putArray("missingEvidence").add("No recommended candidate cost or self-sufficiency exists.");
        var explanations = new ExplanationService();
        explanations.mapper = mapper;
        assertEquals("INFEASIBLE", sources.path("result").path("document").path("status").asText());
        explanations.validate(output.toString(), sources);
        output.withArray("missingEvidence").removeAll();
        assertThrows(ExplanationService.InvalidExplanation.class,
                () -> explanations.validate(output.toString(), sources));
    }

    private static String example(String file) throws Exception {
        return Files.readString(Path.of(System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"), "examples", file));
    }

    @Test
    void reportsSafeReasonsForInvalidModelOutput() throws Exception {
        var explanations = new ExplanationService();
        explanations.mapper = mapper;
        JsonNode sources = mapper.readTree(Files.readString(Path.of(
                System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"), "ai/example-evidence.json")));
        assertEquals("invalid_json", assertThrows(ExplanationService.InvalidExplanation.class,
                () -> explanations.validate("not JSON", sources)).reason());
        assertEquals("invalid_envelope", assertThrows(ExplanationService.InvalidExplanation.class,
                () -> explanations.validate(" ", sources)).reason());

        ObjectNode missing = (ObjectNode) mapper.readTree(example("ai-explanation.json"));
        ((ArrayNode) missing.path("claims").get(0).path("citations")).remove(0);
        assertEquals("missing_required_citations", assertThrows(ExplanationService.InvalidExplanation.class,
                () -> explanations.validate(missing.toString(), sources)).reason());

        ObjectNode incorrectValue = (ObjectNode) mapper.readTree(example("ai-explanation.json"));
        ((ObjectNode) incorrectValue.path("claims").get(0).path("citations").get(0))
                .put("value", "a-different-run");
        assertEquals("citation_value_mismatch", assertThrows(ExplanationService.InvalidExplanation.class,
                () -> explanations.validate(incorrectValue.toString(), sources)).reason());

        ObjectNode wrongPointer = (ObjectNode) mapper.readTree(example("ai-explanation.json"));
        ((ObjectNode) wrongPointer.path("claims").get(0).path("citations").get(0))
                .put("pointer", "/document/runId");
        assertEquals("invalid_citation_pointer", assertThrows(ExplanationService.InvalidExplanation.class,
                () -> explanations.validate(wrongPointer.toString(), sources)).reason());

        ObjectNode wrongCategory = (ObjectNode) mapper.readTree(example("ai-explanation.json"));
        ((ObjectNode) wrongCategory.path("claims").get(0)).put("category", "MODEL_ASSUMPTION");
        assertEquals("invalid_citation_source_pointer_or_category", assertThrows(ExplanationService.InvalidExplanation.class,
                () -> explanations.validate(wrongCategory.toString(), sources)).reason());
    }
}
