package de.eseidinger.variabilityengineering.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.eseidinger.variabilityengineering.energy.ai.ExplanationModel;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.InjectMock;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyRunRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(FixtureAiProfile.class)
class EnergyOptimizationResourceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Inject EnergyEvidenceService evidence;
    @Inject JdbcEnergyRunRepository runs;
    @InjectMock ExplanationModel explanationModel;

    @Test
    void distinguishesInvalidCustomerValuesFromMalformedJson() throws Exception {
        var request = (ObjectNode) mapper.readTree(Files.readString(Path.of(
                System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"), "examples/optimization-request.json")));
        ((ObjectNode) request.path("site")).put("annualDemandKWh", -1);
        var invalid = identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .header("Idempotency-Key", "invalid-demand-" + UUID.randomUUID())
                .contentType("application/json").body(request.toString())
                .when().post("/api/v1/optimization-runs")
                .then().statusCode(422).extract().jsonPath();
        assertEquals("invalid_request", invalid.getString("code"));
        assertEquals("Optimization request violates model constraints.", invalid.getString("message"));
        assertNull(invalid.getString("runId"));

        var malformed = identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .header("Idempotency-Key", "malformed-json-" + UUID.randomUUID())
                .contentType("application/json").body("{invalid")
                .when().post("/api/v1/optimization-runs")
                .then().statusCode(400).extract().jsonPath();
        assertEquals("invalid_json", malformed.getString("code"));
    }

    @Test
    void acceptsAndReturnsOwnedFixtureRun() throws Exception {
        String request = Files.readString(Path.of(System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"),
                "examples/optimization-request.json"));
        String key = "fixture-" + UUID.randomUUID();
        var accepted = identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .header("Idempotency-Key", key).contentType("application/json")
                .body(request).when().post("/api/v1/optimization-runs")
                .then().statusCode(202).extract().jsonPath();
        String runId = accepted.getString("runId");
        assertNotNull(runId);
        assertEquals("QUEUED", accepted.getString("status"));

        var replay = identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .header("Idempotency-Key", key).contentType("application/json")
                .body(request).when().post("/api/v1/optimization-runs")
                .then().statusCode(202).extract().jsonPath();
        assertEquals(runId, replay.getString("runId"));
        var changed = (ObjectNode) mapper.readTree(request);
        ((ObjectNode) changed.path("constraints")).put("maxInitialInvestmentEUR", 17000);
        identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .header("Idempotency-Key", key).contentType("application/json")
                .body(changed.toString()).when().post("/api/v1/optimization-runs")
                .then().statusCode(409);
        ((ObjectNode) changed.path("site")).putArray("existingComponentIds").add("pv-demo-400");
        identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .header("Idempotency-Key", "fixture-" + UUID.randomUUID()).contentType("application/json")
                .body(changed.toString()).when().post("/api/v1/optimization-runs")
                .then().statusCode(422);
        identity("prn_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .when().get("/api/v1/optimization-runs/" + runId).then().statusCode(404);

        String status = null;
        for (int i = 0; i < 50; i++) {
            status = identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                    .when().get("/api/v1/optimization-runs/" + runId)
                    .then().statusCode(200).extract().jsonPath().getString("status");
            if ("SUCCEEDED".equals(status)) break;
            Thread.sleep(200);
        }
        assertEquals("SUCCEEDED", status);
        var page = identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .when().get("/api/v1/optimization-runs/" + runId + "/candidates?page=0&pageSize=20")
                .then().statusCode(200).extract().jsonPath();
        assertEquals(252, page.getInt("total"));
        assertEquals(20, page.getList("items").size());
        var snapshot = evidence.prepare(runs.findOwned(runId, "prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .orElseThrow());
        assertEquals(4, snapshot.size());
        assertEquals(page.getString("items[0].configurationId"),
                snapshot.path("candidate").path("document").path("configurationId").asText());
        when(explanationModel.explain(anyString())).thenAnswer(call ->
                explanationFor(mapper.readTree((String) call.getArgument(0))));
        var explanation = identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .when().post("/api/v1/ai/optimization-runs/" + runId + "/explanation")
                .then().statusCode(200).extract().jsonPath();
        assertEquals(runId, explanation.getString("runId"));
        when(explanationModel.explain(anyString())).thenReturn("{}");
        var rejected = identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .when().post("/api/v1/ai/optimization-runs/" + runId + "/explanation")
                .then().statusCode(502).extract().jsonPath();
        assertEquals("invalid_ai_explanation", rejected.getString("code"));
        assertEquals("The AI explanation could not be validated (invalid_envelope).", rejected.getString("message"));
        var retained = identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .when().get("/api/v1/optimization-runs/" + runId)
                .then().statusCode(200).extract().jsonPath();
        assertEquals("SUCCEEDED", retained.getString("status"));
        when(explanationModel.explain(anyString())).thenThrow(new IllegalStateException("private-provider-detail"));
        var unavailable = identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .when().post("/api/v1/ai/optimization-runs/" + runId + "/explanation")
                .then().statusCode(503).extract().jsonPath();
        assertEquals("ai_unavailable", unavailable.getString("code"));
        assertEquals("AI explanations are not available now.", unavailable.getString("message"));
        var afterFailure = identity("prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .when().get("/api/v1/optimization-runs/" + runId)
                .then().statusCode(200).extract().jsonPath();
        assertEquals(retained.getMap("$"), afterFailure.getMap("$"));
    }

    private String explanationFor(JsonNode sources) throws Exception {
        ObjectNode output = mapper.createObjectNode();
        output.put("schemaVersion", "energy-ai-explanation/v1");
        output.put("runId", sources.path("result").path("document").path("runId").asText());
        ArrayNode claims = output.putArray("claims");
        for (String pointer : new String[]{"/runId", "/dataset/datasetId", "/dataset/version",
                "/modelVersion", "/simulationVersion", "/recommendedConfigurationId",
                "/optimalityStatus"}) cite(claims, sources, "result", pointer);
        cite(claims, sources, "candidate", "/metrics/annualizedCostEUR");
        cite(claims, sources, "candidate", "/metrics/selfSufficiency");
        for (int i = 0; i < sources.path("candidate").path("document").path("constraints").size(); i++) {
            cite(claims, sources, "candidate", "/constraints/" + i);
        }
        output.putArray("missingEvidence").add("Measured customer consumption is unavailable.");
        return output.toString();
    }

    private void cite(ArrayNode claims, JsonNode sources, String source, String pointer) {
        var claim = claims.addObject();
        claim.put("text", "Recorded evidence at " + source + pointer + ".");
        claim.put("category", "CALCULATED_OUTPUT");
        var citation = claim.putArray("citations").addObject();
        citation.put("source", source);
        citation.put("pointer", pointer);
        citation.set("value", sources.path(source).path("document").at(pointer));
    }

    private static RequestSpecification identity(String id) {
        return given().header("X-Platform-Principal-Id", id)
                .header("X-Platform-Principal-Kind", "user")
                .header("X-Platform-Principal-Issuer", "https://identity.example.test")
                .header("X-Platform-Principal-Subject", "fixture-user")
                .header("X-Platform-Principal-Group-Ids", "grp_11111111111111111111111111111111")
                .header("Origin", "https://web-vareng.apps.eseidinger.de")
                .header("X-Platform-CSRF-Token", "test-csrf");
    }
}
