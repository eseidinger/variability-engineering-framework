package de.eseidinger.variabilityengineering.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(FixtureImportProfile.class)
class EnergyOptimizationResourceTest {
    private final ObjectMapper mapper = new ObjectMapper();

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
