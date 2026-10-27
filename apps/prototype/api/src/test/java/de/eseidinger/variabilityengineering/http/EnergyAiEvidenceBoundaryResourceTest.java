package de.eseidinger.variabilityengineering.http;

import de.eseidinger.variabilityengineering.energy.ai.ExplanationModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyRunRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Controlled internal fixtures for the HTTP boundary without a public mutation route. */
@QuarkusTest
@TestProfile(EnergyAiProposalResourceTest.AiProfile.class)
class EnergyAiEvidenceBoundaryResourceTest {
    private static final String RUN_ID = "run-example-0001";
    private static final String PRINCIPAL = "prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @InjectMock JdbcEnergyRunRepository runs;
    @InjectMock EnergyEvidenceService evidence;
    @InjectMock ExplanationModel model;

    @Test
    void keepsCompletedRunAvailableWhenEvidenceIsTooLargeOrIncomplete() throws Exception {
        var run = new JdbcEnergyRunRepository.Run(RUN_ID, PRINCIPAL,
                example("optimization-request.json"), "energy-reference-wasserburg-am-inn-2024",
                "1.0.0", "f".repeat(64), "SUCCEEDED", Instant.parse("2026-10-10T10:00:00Z"),
                null, Instant.parse("2026-10-10T10:00:01Z"),
                example("optimization-result.json"), null, null);
        when(runs.findOwned(RUN_ID, PRINCIPAL)).thenReturn(Optional.of(run));
        String path = "/api/v1/optimization-runs/" + RUN_ID;
        var before = identity().when().get(path).then().statusCode(200).extract().jsonPath();
        assertEquals("SUCCEEDED", before.getString("status"));

        doThrow(new EnergyEvidenceService.EvidenceTooLarge()).when(evidence).prepare(any());
        var oversized = identity().when().post("/api/v1/ai/optimization-runs/" + RUN_ID + "/explanation")
                .then().statusCode(422).extract().jsonPath();
        assertEquals("evidence_unavailable", oversized.getString("code"));
        verifyNoInteractions(model);

        doThrow(new EnergyEvidenceService.InvalidEvidence()).when(evidence).prepare(any());
        var incomplete = identity().when().post("/api/v1/ai/optimization-runs/" + RUN_ID + "/explanation")
                .then().statusCode(422).extract().jsonPath();
        assertEquals("evidence_unavailable", incomplete.getString("code"));
        verifyNoInteractions(model);
        var after = identity().when().get(path).then().statusCode(200).extract().jsonPath();
        assertEquals(before.getMap("$"), after.getMap("$"));
    }

    @Test
    void rejectsForeignSourcesPointersValuesAndRunIdsAtTheHttpBoundary() throws Exception {
        var mapper = new ObjectMapper();
        var run = new JdbcEnergyRunRepository.Run(RUN_ID, PRINCIPAL,
                example("optimization-request.json"), "energy-reference-wasserburg-am-inn-2024",
                "1.0.0", "f".repeat(64), "SUCCEEDED", Instant.parse("2026-10-10T10:00:00Z"),
                null, Instant.parse("2026-10-10T10:00:01Z"), example("optimization-result.json"), null, null);
        when(runs.findOwned(RUN_ID, PRINCIPAL)).thenReturn(Optional.of(run));
        var sources = (ObjectNode) mapper.readTree(Files.readString(Path.of(
                System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"), "ai/example-evidence.json")));
        when(evidence.prepare(any())).thenReturn(sources);
        for (String mutation : new String[]{"source", "pointer", "value", "runId"}) {
            var explanation = (ObjectNode) mapper.readTree(example("ai-explanation.json"));
            var citation = (ObjectNode) explanation.path("claims").get(0).path("citations").get(0);
            if (mutation.equals("runId")) explanation.put("runId", "run-foreign-0001");
            else citation.put(mutation, mutation.equals("pointer") ? "/missing" : "unapproved");
            when(model.explain(any())).thenReturn(explanation.toString());
            assertEquals("invalid_ai_explanation", identity().when()
                    .post("/api/v1/ai/optimization-runs/" + RUN_ID + "/explanation")
                    .then().statusCode(502).extract().jsonPath().getString("code"));
            assertEquals(mapper.readTree(run.resultJson()), mapper.readTree(identity().when()
                    .get("/api/v1/optimization-runs/" + RUN_ID).then().statusCode(200).extract().asString()));
        }
    }

    private static String example(String file) throws Exception {
        return Files.readString(Path.of(System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"), "examples", file));
    }

    private static RequestSpecification identity() {
        return given().header("X-Platform-Principal-Id", PRINCIPAL)
                .header("X-Platform-Principal-Kind", "user")
                .header("X-Platform-Principal-Issuer", "https://identity.example.test")
                .header("X-Platform-Principal-Subject", "fixture-user")
                .header("X-Platform-Principal-Group-Ids", "grp_11111111111111111111111111111111")
                .header("Origin", "https://web-vareng.apps.eseidinger.de")
                .header("X-Platform-CSRF-Token", "test-csrf");
    }
}
