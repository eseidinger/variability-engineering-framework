package de.eseidinger.variabilityengineering.http;

import de.eseidinger.variabilityengineering.energy.ai.ProposalModel;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(EnergyAiProposalResourceTest.AiProfile.class)
class EnergyAiProposalResourceTest {
    @InjectMock ProposalModel model;
    @Inject AgroalDataSource dataSource;
    @Inject @ConfigProperty(name = "quarkus.langchain4j.openai.timeout") Duration providerTimeout;
    @Inject @ConfigProperty(name = "quarkus.langchain4j.openai.chat-model.response-format") String responseFormat;

    @Test
    void allowsLongerOpenAiCalls() {
        assertEquals(Duration.ofSeconds(180), providerTimeout);
        assertEquals("json_object", responseFormat);
    }

    @Test
    void returnsValidatedProposalWithoutStartingRun() throws Exception {
        when(model.propose(anyString())).thenReturn(Files.readString(Path.of(
                System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"), "examples/ai-request-proposal.json")));
        identity().contentType("application/json")
                .body("{\"description\":\"35 m2 roof, 4500 kWh, 18000 EUR, 0.55 self-sufficiency, version 1.0.0\"}")
                .when().post("/api/v1/ai/request-proposals")
                .then().statusCode(200);
        given().contentType("application/json").body("{\"description\":\"hello\"}")
                .when().post("/api/v1/ai/request-proposals").then().statusCode(401);
    }

    @Test
    void rejectsMalformedModelOutput() {
        when(model.propose(anyString())).thenReturn("{}");
        identity().contentType("application/json").body("{\"description\":\"complete inputs\"}")
                .when().post("/api/v1/ai/request-proposals").then().statusCode(502)
                .body("message", org.hamcrest.Matchers.containsString("invalid_envelope"));
    }

    @Test
    void providerFailureDoesNotCreateRunOrExposeProviderDetails() throws Exception {
        long before = runCount();
        when(model.propose(anyString())).thenThrow(new IllegalStateException("private-provider-detail"));
        var response = identity().contentType("application/json").body("{\"description\":\"complete inputs\"}")
                .when().post("/api/v1/ai/request-proposals")
                .then().statusCode(503).extract().jsonPath();
        assertEquals("ai_unavailable", response.getString("code"));
        assertEquals("AI proposals are not available now.", response.getString("message"));
        assertEquals(before, runCount());
    }

    private long runCount() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM energy_optimization_run")) {
            result.next();
            return result.getLong(1);
        }
    }

    @Test
    void explanationRequiresOwnershipBeforeModelAccess() {
        identity().when().post("/api/v1/ai/optimization-runs/run-unknown-0001/explanation")
                .then().statusCode(404);
        given().when().post("/api/v1/ai/optimization-runs/run-unknown-0001/explanation")
                .then().statusCode(401);
    }

    private static io.restassured.specification.RequestSpecification identity() {
        return given().header("X-Platform-Principal-Id", "prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .header("X-Platform-Principal-Kind", "user")
                .header("X-Platform-Principal-Issuer", "https://identity.example.test")
                .header("X-Platform-Principal-Subject", "fixture-user")
                .header("X-Platform-Principal-Group-Ids", "grp_11111111111111111111111111111111");
    }

    public static class AiProfile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() {
            return Map.of("energy.ai.enabled", "true", "quarkus.langchain4j.openai.enable-integration", "true");
        }
    }
}
