package de.eseidinger.variabilityengineering.energy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationVerifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyPublicationVerifierTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EnergyPublicationVerifier verifier = new EnergyPublicationVerifier();

    @Test
    void verifiesTheReleasedBytesAndKeepsImmutableCopies() throws IOException {
        var fixture = fixture();
        var publication = verifier.verify(fixture.manifest(), fixture.artifacts());
        assertEquals("252b90b18b594a764a2744ba133a8db56b233e9e375301befc7616e9c18c1715",
                publication.manifestSha256());
        assertEquals(8_784, publication.profile().hours().size());
        assertEquals(5, publication.artifactBytes().size());
        assertEquals(1, publication.warnings().size());
        var originalManifest = publication.manifestBytes();
        fixture.manifest()[0] ^= 1;
        assertArrayEquals(originalManifest, publication.manifestBytes());
        var artifact = publication.artifactBytes().get("pv-yield");
        artifact[0] ^= 1;
        assertArrayEquals(fixture.artifacts().get("pv-yield"), publication.artifactBytes().get("pv-yield"));
    }

    @Test
    void rejectsNonCalendarPublicationPeriodsBeforeReadingSeries() throws IOException {
        var fixture = fixture();
        for (var times : new String[][] {
                {"2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z"},
                {"2024-01-01T00:00:00Z", "2024-12-31T00:00:00Z"},
                {"2024-01-01T01:00:00Z", "2025-01-01T01:00:00Z"},
                {"2023-02-01T00:00:00Z", "2024-02-01T00:00:00Z"}}) {
            var manifest = (ObjectNode) MAPPER.readTree(fixture.manifest());
            var period = (ObjectNode) manifest.path("metadata").path("period");
            period.put("start", times[0]);
            period.put("endExclusive", times[1]);
            var error = assertThrows(IllegalArgumentException.class,
                    () -> verifier.verify(MAPPER.writeValueAsBytes(manifest), fixture.artifacts()));
            assertEquals("energy profiles require one complete UTC calendar year", error.getMessage());
        }
    }

    @Test
    void rejectsDigestMismatchAndMissingArtifact() throws IOException {
        var fixture = fixture();
        fixture.artifacts().get("pv-yield")[0] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(fixture.manifest(), fixture.artifacts()));
        var missing = fixture();
        missing.artifacts().remove("pv-yield");
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(missing.manifest(), missing.artifacts()));
    }

    @Test
    void rejectsUnpublishedUnsafeAndWrongUnitManifests() throws IOException {
        var fixture = fixture();
        for (var change : new String[] {"status", "path", "unit", "quality"}) {
            var manifest = (ObjectNode) MAPPER.readTree(fixture.manifest());
            switch (change) {
                case "status" -> manifest.put("status", "DRAFT");
                case "path" -> ((ObjectNode) manifest.path("artifacts").get(0)).put("path", "../outside");
                case "unit" -> ((ObjectNode) manifest.path("artifacts").get(0)).put("unit", "kW");
                case "quality" -> ((ObjectNode) manifest.path("quality")).put("status", "FAIL");
                default -> throw new AssertionError();
            }
            assertThrows(IllegalArgumentException.class,
                    () -> verifier.verify(MAPPER.writeValueAsBytes(manifest), fixture.artifacts()), change);
        }
    }

    @Test
    void rejectsSemanticallyInvalidSeriesEvenWithMatchingDigest() throws Exception {
        for (var change : new String[] {"missing_hour", "negative", "wrong_period", "wrong_load_sum"}) {
            var fixture = fixture();
            var manifest = (ObjectNode) MAPPER.readTree(fixture.manifest());
            String id = change.equals("wrong_load_sum") ? "reference-load-shape" : "pv-yield";
            var artifact = (ObjectNode) MAPPER.readTree(fixture.artifacts().get(id));
            switch (change) {
                case "missing_hour" -> ((com.fasterxml.jackson.databind.node.ArrayNode) artifact.path("values")).remove(1);
                case "negative" -> ((ObjectNode) artifact.path("values").get(1)).put("value", -1);
                case "wrong_period" -> ((ObjectNode) artifact.path("period")).put("endExclusive", "2024-12-31T00:00:00Z");
                case "wrong_load_sum" -> ((ObjectNode) artifact.path("values").get(1)).put("value", 0.5);
                default -> throw new AssertionError();
            }
            byte[] bytes = MAPPER.writeValueAsBytes(artifact);
            fixture.artifacts().put(id, bytes);
            for (var descriptor : manifest.path("artifacts")) {
                if (id.equals(descriptor.path("artifactId").asText())) {
                    ((ObjectNode) descriptor).put("sha256", sha256(bytes));
                }
            }
            assertThrows(IllegalArgumentException.class,
                    () -> verifier.verify(MAPPER.writeValueAsBytes(manifest), fixture.artifacts()), change);
        }
    }

    @Test
    void enforcesByteLimitsBeforeParsing() throws IOException {
        var fixture = fixture();
        var oversized = new byte[EnergyPublicationVerifier.MAX_MANIFEST_BYTES + 1];
        Arrays.fill(oversized, (byte) ' ');
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(oversized, fixture.artifacts()));
        fixture.artifacts().put("pv-yield", new byte[EnergyPublicationVerifier.MAX_ARTIFACT_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(fixture.manifest(), fixture.artifacts()));
    }

    private static Fixture fixture() throws IOException {
        var prefix = "fixtures/energy-reference-wasserburg-am-inn-2024/";
        byte[] manifest = Files.readAllBytes(EnergyFixture.kitPath(prefix + "manifest.json"));
        var artifacts = new HashMap<String, byte[]>();
        for (var descriptor : MAPPER.readTree(manifest).path("artifacts")) {
            String id = descriptor.path("artifactId").asText();
            artifacts.put(id, Files.readAllBytes(EnergyFixture.kitPath(prefix + "artifacts/" + id + ".json")));
        }
        return new Fixture(manifest, artifacts);
    }

    private record Fixture(byte[] manifest, Map<String, byte[]> artifacts) { }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
