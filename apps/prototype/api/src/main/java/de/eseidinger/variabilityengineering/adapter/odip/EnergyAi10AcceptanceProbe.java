package de.eseidinger.variabilityengineering.adapter.odip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.eseidinger.variabilityengineering.energy.EnergyOptimizationRequest;
import de.eseidinger.variabilityengineering.energy.EnergyOptimizer;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.zip.ZipInputStream;

/** Synthetic-only Gate 5 regression probe for a disposable private test component. */
public final class EnergyAi10AcceptanceProbe {
    private static final Logger LOG = Logger.getLogger(EnergyAi10AcceptanceProbe.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ID = "energy-reference-wasserburg-am-inn-2024";
    private static final String ORIGINAL_SHA = "252b90b18b594a764a2744ba133a8db56b233e9e375301befc7616e9c18c1715";
    private static final String CORRECTED_SHA = "ebfd2cc7dcdf73cef4ed81a94b693ec23c4448460c8161ca648369ea0a930f93";
    private static final String ARCHIVE_SHA = "8fbb0cf6fb7d65da84be8ee554b5ae48abc808bd4cdb048787f99649d46e9d74";
    private static final int EXPECTED_CASES = 10;

    private EnergyAi10AcceptanceProbe() { }

    public static void main(String[] args) {
        if (args.length != 1 || !"--synthetic-only".equals(args[0])) {
            throw new IllegalArgumentException("synthetic-only flag required");
        }
        configureLogging();
        try {
            run();
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.errorf("energy_ai10_acceptance status=FAIL cause=%s",
                    exception.getClass().getSimpleName());
            throw new IllegalStateException("synthetic AI-10 acceptance probe failed");
        }
    }

    private static void run() throws IOException, InterruptedException {
        Map<String, byte[]> archive = archive();
        Fixture original = fixture(archive, "original");
        Fixture corrected = fixture(archive, "corrected");
        var verifier = new EnergyPublicationVerifier();
        var first = verifier.verify(original.manifest(), original.artifacts());
        check(ORIGINAL_SHA.equals(first.manifestSha256()), "original manifest digest");
        check(first.profile().hours().size() == 8784, "original calendar-year hours");
        var second = verifier.verify(corrected.manifest(), corrected.artifacts());
        check(CORRECTED_SHA.equals(second.manifestSha256()), "corrected manifest digest");
        check(second.profile().hours().size() == 8784, "corrected calendar-year hours");

        conformance(original, first, "original_oracle");
        conformance(corrected, second, "corrected_oracle");
        check(first.profile().version().equals("1.0.0")
                && second.profile().version().equals("1.0.1"), "version isolation");
        passed("version_isolation");

        var transport = new FixtureTransport(original);
        var client = new EnergyPublicationClient(URI.create("https://odip.example.test"),
                () -> "synthetic-token", transport);
        var cached = client.fetchExact(ID, "1.0.0", null);
        check(ORIGINAL_SHA.equals(cached.publication().manifestSha256())
                && transport.calls == 6, "initial exact import");
        var revalidated = client.fetchExact(ID, "1.0.0", cached);
        check(ORIGINAL_SHA.equals(revalidated.publication().manifestSha256())
                && transport.calls == 12 && transport.notModified == 6, "conditional revalidation");
        passed("exact_import_cache");

        var changedBytes = original.copy();
        changedBytes.artifacts().get("pv-yield")[0] ^= 1;
        reject(verifier, changedBytes, cached, "tampered_read");

        var wrongUnit = original.copy();
        var unitManifest = (ObjectNode) MAPPER.readTree(wrongUnit.manifest());
        ((ObjectNode) unitManifest.path("artifacts").get(0)).put("unit", "kW");
        reject(verifier, wrongUnit.withManifest(MAPPER.writeValueAsBytes(unitManifest)), cached, "wrong_unit");

        var missingHour = original.copy();
        var missingManifest = (ObjectNode) MAPPER.readTree(missingHour.manifest());
        var pv = (ObjectNode) MAPPER.readTree(missingHour.artifacts().get("pv-yield"));
        ((com.fasterxml.jackson.databind.node.ArrayNode) pv.path("values")).remove(1);
        byte[] shortened = MAPPER.writeValueAsBytes(pv);
        missingHour.artifacts().put("pv-yield", shortened);
        for (JsonNode descriptor : missingManifest.path("artifacts")) {
            if ("pv-yield".equals(descriptor.path("artifactId").asText())) {
                ((ObjectNode) descriptor).put("sha256", sha256(shortened));
            }
        }
        reject(verifier, missingHour.withManifest(MAPPER.writeValueAsBytes(missingManifest)),
                cached, "missing_hour_matching_digest");

        var unsupported = original.copy();
        var unsupportedManifest = (ObjectNode) MAPPER.readTree(unsupported.manifest());
        unsupportedManifest.put("schemaRef", "urn:portfolio:energy-configuration-input:v99");
        reject(verifier, unsupported.withManifest(MAPPER.writeValueAsBytes(unsupportedManifest)),
                cached, "unsupported_schema");

        var unpublished = original.copy();
        var unpublishedManifest = (ObjectNode) MAPPER.readTree(unpublished.manifest());
        unpublishedManifest.put("status", "DRAFT");
        reject(verifier, unpublished.withManifest(MAPPER.writeValueAsBytes(unpublishedManifest)),
                cached, "unpublished");

        try {
            client.fetchExact(ID, "1.0.1", cached);
            throw new IllegalStateException("cross-version cache accepted");
        } catch (IllegalArgumentException expected) {
            check(transport.calls == 12, "cross-version request reached transport");
        }
        passed("cross_version_cache");

        check(Arrays.equals(original.manifest(), cached.publication().manifestBytes()),
                "cached original manifest changed");
        check(ORIGINAL_SHA.equals(verifier.verify(original.manifest(), original.artifacts())
                .manifestSha256()), "original publication changed");
        check(CORRECTED_SHA.equals(verifier.verify(corrected.manifest(), corrected.artifacts())
                .manifestSha256()), "corrected publication changed");
        LOG.infof("energy_ai10_acceptance status=PASS cases=%d", EXPECTED_CASES);
    }

    private static void conformance(Fixture fixture, VerifiedEnergyPublication publication, String name)
            throws IOException {
        EnergyOptimizationRequest request = MAPPER.readValue(fixture.request(), EnergyOptimizationRequest.class);
        JsonNode expected = MAPPER.readTree(fixture.expected());
        var actual = new EnergyOptimizer().optimize(request, publication.catalog(), publication.profile());
        check(actual.candidates().size() == 252
                && expected.path("candidates").size() == 252, "candidate count");
        check(actual.feasibleCandidateCount() == expected.path("result").path("feasibleCandidateCount").asInt(),
                "feasible count");
        check(actual.status().equals(expected.path("result").path("status").asText())
                && actual.optimalityStatus().equals(expected.path("result").path("optimalityStatus").asText())
                && actual.recommendedConfigurationId().equals(expected.path("result")
                    .path("recommendedConfigurationId").asText()), "result summary");
        var expectedRanking = new java.util.ArrayList<String>();
        expected.path("ranking").forEach(node -> expectedRanking.add(node.asText()));
        check(actual.ranking().equals(expectedRanking), "complete ranking");
        for (int index = 0; index < 252; index++) {
            var candidate = actual.candidates().get(index);
            var oracle = expected.path("candidates").get(index);
            check(candidate.configuration().id().equals(oracle.path("configurationId").asText())
                    && candidate.feasible() == oracle.path("feasible").asBoolean(), "candidate identity/feasibility");
            check(candidate.constraints().size() == oracle.path("constraints").size(), "constraint count");
            for (int constraintIndex = 0; constraintIndex < candidate.constraints().size(); constraintIndex++) {
                var constraint = candidate.constraints().get(constraintIndex);
                var reference = oracle.path("constraints").get(constraintIndex);
                check(constraint.code().equals(reference.path("code").asText())
                        && constraint.status().equals(reference.path("status").asText())
                        && same(constraint.actual(), reference.path("actual"))
                        && same(constraint.limit(), reference.path("limit"))
                        && (constraint.unit() == null ? reference.path("unit").isMissingNode()
                            : constraint.unit().equals(reference.path("unit").asText())),
                        "constraint mismatch");
            }
            JsonNode metrics = MAPPER.valueToTree(candidate.metrics());
            for (var metric : oracle.path("metrics").properties()) {
                check(metrics.path(metric.getKey()).isNumber()
                        && Math.abs(metrics.path(metric.getKey()).asDouble() - metric.getValue().asDouble()) < 1e-6,
                        "metric mismatch");
            }
        }
        passed(name);
    }

    private static boolean same(Object actual, JsonNode expected) {
        if (actual instanceof Number number && expected.isNumber()) {
            return Math.abs(number.doubleValue() - expected.asDouble()) < 1e-9;
        }
        return actual != null && actual.toString().equals(expected.asText());
    }

    private static void reject(EnergyPublicationVerifier verifier, Fixture changed,
                               EnergyPublicationClient.Cached cached, String name)
            throws IOException, InterruptedException {
        try {
            verifier.verify(changed.manifest(), changed.artifacts());
            throw new IllegalStateException("invalid publication accepted by verifier");
        } catch (IllegalArgumentException expected) {
            // The real verifier rejects the controlled corruption.
        }
        var client = new EnergyPublicationClient(URI.create("https://odip.example.test"),
                () -> "synthetic-token", new FixtureTransport(changed));
        try {
            client.fetchExact(ID, "1.0.0", cached);
            throw new IllegalStateException("invalid publication accepted by exact import");
        } catch (IllegalArgumentException | IOException expected) {
            check(ORIGINAL_SHA.equals(cached.publication().manifestSha256()), "verified cache changed");
        }
        passed(name);
    }

    private static Fixture fixture(Map<String, byte[]> archive, String label) throws IOException {
        byte[] manifest = archive.get(label + "/manifest.json");
        var artifacts = new HashMap<String, byte[]>();
        for (JsonNode descriptor : MAPPER.readTree(manifest).path("artifacts")) {
            String id = descriptor.path("artifactId").asText();
            byte[] bytes = archive.get(label + "/artifacts/" + id + ".json");
            check(bytes != null, "missing packaged artifact");
            artifacts.put(id, bytes.clone());
        }
        return new Fixture(manifest.clone(), artifacts,
                archive.get(label + "/request.json"), archive.get(label + "/expected.json"));
    }

    private static Map<String, byte[]> archive() throws IOException {
        byte[] bytes;
        try (var stream = EnergyAi10AcceptanceProbe.class.getResourceAsStream(
                "/energy-acceptance/ai10-fixtures.zip")) {
            if (stream == null) throw new IOException("missing packaged AI-10 fixtures");
            bytes = stream.readAllBytes();
        }
        check(ARCHIVE_SHA.equals(sha256(bytes)), "fixture archive digest");
        var contents = new HashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                check(!entry.isDirectory() && entry.getName().matches(
                        "^(original|corrected)/(manifest|request|expected)\\.json$|"
                        + "^(original|corrected)/artifacts/[a-z-]+\\.json$"), "unexpected fixture path");
                byte[] content = zip.readNBytes(3 * 1024 * 1024);
                check(content.length < 3 * 1024 * 1024 && contents.putIfAbsent(entry.getName(), content) == null,
                        "oversized or duplicate fixture");
            }
        }
        check(contents.size() == 16, "fixture archive entry count");
        return Map.copyOf(contents);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new IllegalStateException(label);
    }

    private static void passed(String name) {
        LOG.infof("energy_ai10_acceptance case=%s status=PASS", name);
    }

    private static void configureLogging() {
        var root = java.util.logging.Logger.getLogger("");
        for (var existing : root.getHandlers()) root.removeHandler(existing);
        var handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + System.lineSeparator();
            }
        });
        root.addHandler(handler);
        root.setLevel(Level.INFO);
    }

    private record Fixture(byte[] manifest, Map<String, byte[]> artifacts,
                           byte[] request, byte[] expected) {
        Fixture copy() {
            var copies = new HashMap<String, byte[]>();
            artifacts.forEach((id, bytes) -> copies.put(id, bytes.clone()));
            return new Fixture(manifest.clone(), copies, request.clone(), expected.clone());
        }

        Fixture withManifest(byte[] replacement) {
            return new Fixture(replacement, artifacts, request, expected);
        }
    }

    private static final class FixtureTransport implements EnergyPublicationClient.Transport {
        private final Fixture fixture;
        private int calls;
        private int notModified;

        FixtureTransport(Fixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public EnergyPublicationClient.Response get(URI uri, String token, String etag, int maxBytes)
                throws IOException {
            calls++;
            check("https://odip.example.test".equals(uri.getScheme() + "://" + uri.getHost())
                    && "synthetic-token".equals(token), "unexpected transport context");
            String path = uri.getPath();
            String prefix = "/api/v1/datasets/" + ID + "/versions/1.0.0";
            byte[] bytes;
            if (path.equals(prefix)) {
                bytes = fixture.manifest();
            } else if (path.startsWith(prefix + "/artifacts/")) {
                bytes = fixture.artifacts().get(path.substring((prefix + "/artifacts/").length()));
            } else throw new IOException("unexpected synthetic path");
            if (bytes == null || bytes.length > maxBytes) throw new IOException("missing synthetic bytes");
            String actualEtag = "\"" + sha256(bytes) + "\"";
            if (actualEtag.equals(etag)) {
                notModified++;
                return new EnergyPublicationClient.Response(304, actualEtag, null);
            }
            return new EnergyPublicationClient.Response(200, actualEtag, bytes);
        }
    }
}
