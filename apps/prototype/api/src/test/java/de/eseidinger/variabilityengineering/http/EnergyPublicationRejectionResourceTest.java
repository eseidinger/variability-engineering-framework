package de.eseidinger.variabilityengineering.http;

import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationClient;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationImportService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@QuarkusTest
@TestProfile(EnergyAiProposalResourceTest.AiProfile.class)
class EnergyPublicationRejectionResourceTest {
    @InjectMock EnergyPublicationImportService publications;
    @Inject AgroalDataSource dataSource;

    @Test
    void distinguishesMissingUnpublishedAndUnavailableWithoutCreatingRuns() throws Exception {
        String request = Files.readString(Path.of(System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"),
                "examples/optimization-request.json"));
        long before = runCount();
        int[] upstreamStatuses = {404, 409, 503};
        int[] expectedStatuses = {422, 422, 503};
        String[] expectedCodes = {"publication_not_found", "publication_not_published", "publication_unavailable"};
        for (int i = 0; i < upstreamStatuses.length; i++) {
            doThrow(new EnergyPublicationClient.HttpStatusException(upstreamStatuses[i]))
                    .when(publications).importExact(anyString(), anyString());
            var response = given()
                    .header("X-Platform-Principal-Id", "prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                    .header("X-Platform-Principal-Kind", "user")
                    .header("X-Platform-Principal-Issuer", "https://identity.example.test")
                    .header("X-Platform-Principal-Subject", "fixture-user")
                    .header("X-Platform-Principal-Group-Ids", "grp_11111111111111111111111111111111")
                    .header("Idempotency-Key", "publication-rejection-" + UUID.randomUUID())
                    .contentType("application/json").body(request)
                    .when().post("/api/v1/optimization-runs")
                    .then().statusCode(expectedStatuses[i]).extract().jsonPath();
            assertEquals(expectedCodes[i], response.getString("code"));
            assertNull(response.getString("runId"));
            assertEquals(before, runCount());
        }
    }

    private long runCount() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM energy_optimization_run")) {
            result.next();
            return result.getLong(1);
        }
    }
}
