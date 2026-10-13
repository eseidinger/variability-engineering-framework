package de.eseidinger.variabilityengineering.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationClient;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationVerifier;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class JdbcEnergyRunRepositoryTest {
    private static final String DATASET = "energy-reference-wasserburg-am-inn-2024";
    private static final String VERSION = "1.0.0";
    private static final String OWNER = "prn_11111111111111111111111111111111";
    private static final String OTHER = "prn_22222222222222222222222222222222";

    @Inject JdbcEnergyRunRepository runs;
    @Inject JdbcEnergyPublicationRepository publications;
    @Inject ObjectMapper mapper;

    @Test
    void persistsOneOwnedRunAndImmutableCandidatePages() throws Exception {
        Path fixture = Path.of(System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"),
                "fixtures/energy-reference-wasserburg-am-inn-2024");
        byte[] manifest = Files.readAllBytes(fixture.resolve("manifest.json"));
        var artifacts = new HashMap<String, byte[]>();
        for (var descriptor : mapper.readTree(manifest).path("artifacts")) {
            String id = descriptor.path("artifactId").asText();
            artifacts.put(id, Files.readAllBytes(fixture.resolve("artifacts/" + id + ".json")));
        }
        var verified = new EnergyPublicationVerifier().verify(manifest, artifacts);
        publications.saveIfAbsent(new EnergyPublicationClient.Cached(verified, null, Map.of()));
        String request = Files.readString(Path.of(System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"),
                "examples/optimization-request.json"));
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.getBytes()));
        String key = "test-" + UUID.randomUUID();
        var first = runs.reserve(OWNER, key, hash, request, DATASET, VERSION, verified.manifestSha256());
        assertEquals(first.runId(), runs.reserve(OWNER, key, hash, request, DATASET, VERSION,
                verified.manifestSha256()).runId());
        assertThrows(JdbcEnergyRunRepository.IdempotencyConflict.class, () -> runs.reserve(
                OWNER, key, "a".repeat(64), request, DATASET, VERSION, verified.manifestSha256()));
        assertTrue(runs.findOwned(first.runId(), OTHER).isEmpty());

        var headers = given().header("X-Platform-Principal-Id", OWNER)
                .header("X-Platform-Principal-Kind", "user")
                .header("X-Platform-Principal-Issuer", "https://identity.example.test")
                .header("X-Platform-Principal-Subject", "test-user")
                .header("X-Platform-Principal-Group-Ids", "grp_11111111111111111111111111111111");
        headers.when().get("/api/v1/optimization-runs/" + first.runId())
                .then().statusCode(200);
        given().header("X-Platform-Principal-Id", OTHER)
                .header("X-Platform-Principal-Kind", "user")
                .header("X-Platform-Principal-Issuer", "https://identity.example.test")
                .header("X-Platform-Principal-Subject", "other-user")
                .header("X-Platform-Principal-Group-Ids", "grp_11111111111111111111111111111111")
                .when().get("/api/v1/optimization-runs/" + first.runId()).then().statusCode(404);
        given().when().get("/api/v1/capabilities").then().statusCode(401);

        JdbcEnergyRunRepository.Run completed = first;
        for (int i = 0; i < 50; i++) {
            completed = runs.findOwned(first.runId(), OWNER).orElseThrow();
            if ("SUCCEEDED".equals(completed.status())) break;
            Thread.sleep(200);
        }
        assertEquals("SUCCEEDED", completed.status(), completed.errorJson());
        var result = mapper.readTree(completed.resultJson());
        assertEquals(252, result.path("evaluatedCandidateCount").asInt());
        assertEquals(194, result.path("feasibleCandidateCount").asInt());
        assertEquals(verified.manifestSha256(), result.path("dataset").path("manifestSha256").asText());
        assertEquals(252, runs.candidates(first.runId(), 0, 20).total());
        assertEquals(result.path("recommendedConfigurationId").asText(), mapper.readTree(
                runs.candidates(first.runId(), 0, 20).items().getFirst()).path("configurationId").asText());
        headers.when().get("/api/v1/optimization-runs/" + first.runId() + "/candidates?page=0&pageSize=20")
                .then().statusCode(200);
    }
}
