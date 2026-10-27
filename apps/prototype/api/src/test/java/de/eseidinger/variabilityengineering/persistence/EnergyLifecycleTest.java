package de.eseidinger.variabilityengineering.persistence;

import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationClient;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationVerifier;
import de.eseidinger.variabilityengineering.energy.EnergyContracts;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(EnergyLifecycleTest.Profile.class)
class EnergyLifecycleTest {
    private static final String OWNER = "prn_dddddddddddddddddddddddddddddddd";
    private static final String DATASET = "energy-lifecycle-" + UUID.randomUUID();
    @Inject DataSource dataSource;
    @Inject JdbcEnergyRunRepository runs;
    @Inject JdbcEnergyPublicationRepository publications;
    @Inject EnergyRetention retention;

    public static class Profile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.scheduler.enabled", "false");
        }
    }

    @BeforeEach
    @AfterEach
    void resetTestRows() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM energy_optimization_run WHERE principal_id = '" + OWNER + "'");
            statement.executeUpdate("DELETE FROM energy_security_audit WHERE principal_id = '" + OWNER + "'");
        }
    }

    private JdbcEnergyRunRepository.Run reserve(String key) throws Exception {
        Path fixture = Path.of(System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"),
                "fixtures/energy-reference-wasserburg-am-inn-2024");
        byte[] manifest = Files.readAllBytes(fixture.resolve("manifest.json"));
        var artifacts = new HashMap<String, byte[]>();
        for (var descriptor : EnergyContracts.parse(manifest).path("artifacts")) {
            String id = descriptor.path("artifactId").asText();
            artifacts.put(id, Files.readAllBytes(fixture.resolve("artifacts/" + id + ".json")));
        }
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) EnergyContracts.parse(manifest);
        document.put("datasetId", DATASET);
        manifest = document.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var verified = new EnergyPublicationVerifier().verify(manifest, artifacts);
        publications.saveIfAbsent(new EnergyPublicationClient.Cached(verified, null, Map.of()));
        return runs.reserve(OWNER, key, "a".repeat(64), "{}", DATASET,
                "1.0.0", verified.manifestSha256());
    }

    @Test
    void expiresTerminalRunsCandidatesAndAuditAtTheBoundaryButKeepsActiveWork() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant cutoff = now.minus(180, ChronoUnit.DAYS);
        String expiredKey = UUID.randomUUID().toString();
        var old = reserve(expiredKey);
        var recent = reserve(UUID.randomUUID().toString());
        var active = reserve(UUID.randomUUID().toString());
        for (String status : List.of("SUCCEEDED", "INFEASIBLE", "FAILED")) {
            var terminal = reserve(UUID.randomUUID().toString());
            finishAt(terminal.runId(), status, cutoff.minusSeconds(1));
        }
        finishAt(old.runId(), "SUCCEEDED", cutoff);
        finishAt(recent.runId(), "SUCCEEDED", cutoff.plusSeconds(1));
        retention.audit(OWNER, old.runId(), "READ_RUN", true);
        retention.audit(OWNER, recent.runId(), "EXPLAIN_RUN", false);
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "UPDATE energy_security_audit SET occurred_at=? WHERE run_id=?")) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            statement.setString(2, old.runId());
            statement.executeUpdate();
        }
        var purged = retention.purgeExpired(now);
        assertEquals(4, purged.runs());
        assertEquals(1, purged.auditEvents());
        assertTrue(runs.findOwned(old.runId(), OWNER).isEmpty());
        assertTrue(runs.findByIdempotency(OWNER, expiredKey).isEmpty());
        assertEquals(0, runs.candidates(old.runId(), 0, 100).total());
        assertTrue(runs.findOwned(recent.runId(), OWNER).isPresent());
        assertTrue(runs.findOwned(active.runId(), OWNER).isPresent());
        assertTrue(publications.find(old.datasetId(), old.version()).isPresent());
        assertEquals(0, retention.purgeExpired(now).runs());
    }

    private void finishAt(String id, String status, Instant completed) throws Exception {
        try (var connection = dataSource.getConnection()) {
            try (var update = connection.prepareStatement(
                    "UPDATE energy_optimization_run SET status=?, completed_at=? WHERE run_id=?")) {
                update.setString(1, status);
                update.setTimestamp(2, Timestamp.from(completed));
                update.setString(3, id);
                update.executeUpdate();
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO energy_optimization_candidate VALUES (?, 0, '{}'::jsonb)")) {
                insert.setString(1, id);
                insert.executeUpdate();
            }
        }
    }

    @Test
    void concurrentReservationsAndClaimsAreUniqueAndStaleWorkersCannotCompleteAfterRecovery() throws Exception {
        String key = UUID.randomUUID().toString();
        var seed = reserve(key);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> reserve = () -> runs.reserve(OWNER, key, "a".repeat(64), "{}", seed.datasetId(),
                    seed.version(), seed.manifestSha256()).runId();
            var results = executor.invokeAll(List.of(reserve, reserve));
            assertEquals(seed.runId(), results.get(0).get());
            assertEquals(seed.runId(), results.get(1).get());
            Callable<JdbcEnergyRunRepository.Claimed> claim = () -> runs.claimNext().orElseThrow();
            // Ensure two claimable jobs regardless of test order.
            reserve(UUID.randomUUID().toString());
            var claims = executor.invokeAll(List.of(claim, claim));
            var first = claims.get(0).get();
            var second = claims.get(1).get();
            assertFalse(first.run().runId().equals(second.run().runId()));
            runs.requeueInterrupted();
            runs.complete(first, "SUCCEEDED", "{}", null, List.of("{}"));
            assertEquals("QUEUED", runs.findOwned(first.run().runId(), OWNER).orElseThrow().status());
            assertEquals(0, runs.candidates(first.run().runId(), 0, 100).total());
            var reclaimed = runs.claimNext().orElseThrow();
            runs.complete(reclaimed, "FAILED", null, "{}", List.of());
            assertEquals("FAILED", runs.findOwned(reclaimed.run().runId(), OWNER).orElseThrow().status());
        }
    }
}
