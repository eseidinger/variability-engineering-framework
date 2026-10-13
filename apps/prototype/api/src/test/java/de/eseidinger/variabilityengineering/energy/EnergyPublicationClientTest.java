package de.eseidinger.variabilityengineering.energy;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationClient;
import de.eseidinger.variabilityengineering.adapter.odip.ManagedClientCredentialsTokenProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyPublicationClientTest {
    private static final String ID = "energy-reference-wasserburg-am-inn-2024";
    private static final String VERSION = "1.0.0";
    private static final String PREFIX = "/api/v1/datasets/" + ID + "/versions/" + VERSION;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fetchesAnExactVersionAndRevalidatesWithEtags() throws Exception {
        var transport = new FixtureTransport();
        var client = new EnergyPublicationClient(URI.create("https://odip.example"), () -> "fixture-token", transport);
        var first = client.fetchExact(ID, VERSION, null);
        assertEquals(8_784, first.publication().profile().hours().size());
        assertEquals("\"" + first.publication().manifestSha256() + "\"", first.manifestEtag());
        assertEquals(6, transport.calls);
        var second = client.fetchExact(ID, VERSION, first);
        assertEquals(first.publication().manifestSha256(), second.publication().manifestSha256());
        assertEquals(12, transport.calls);
        assertEquals(6, transport.conditionalCalls);
    }

    @Test
    void rejectsCrossVersionCacheRedirectsAndUncached304() throws Exception {
        var transport = new FixtureTransport();
        var client = new EnergyPublicationClient(URI.create("https://odip.example"), () -> "token", transport);
        assertThrows(IllegalArgumentException.class,
                () -> new EnergyPublicationClient(URI.create("http://odip.example"), () -> "token", transport));
        assertThrows(IllegalArgumentException.class, () -> client.fetchExact("../escape", VERSION, null));
        var cached = client.fetchExact(ID, VERSION, null);
        assertThrows(IllegalArgumentException.class, () -> client.fetchExact(ID, "1.0.1", cached));
        transport.forceStatus = 302;
        assertThrows(IOException.class, () -> client.fetchExact(ID, VERSION, cached));
        transport.forceStatus = 304;
        assertThrows(IOException.class, () -> client.fetchExact(ID, VERSION, null));
        for (int status : new int[] {403, 409}) {
            transport.forceStatus = status;
            assertThrows(IOException.class, () -> client.fetchExact(ID, VERSION, cached));
        }
    }

    @Test
    void requiresManagedHttpsTokenBinding() {
        assertThrows(IllegalArgumentException.class, () -> new ManagedClientCredentialsTokenProvider(
                "ODIP", Map.of("ODIP_CLIENT_ID", "id", "ODIP_CLIENT_SECRET", "secret",
                        "ODIP_TOKEN_URL", "http://token.example/oauth")));
        assertThrows(IllegalArgumentException.class,
                () -> new ManagedClientCredentialsTokenProvider("ODIP", Map.of()));
    }

    private static final class FixtureTransport implements EnergyPublicationClient.Transport {
        private final byte[] manifest;
        private final Map<String, byte[]> artifacts = new HashMap<>();
        private int calls;
        private int conditionalCalls;
        private int forceStatus;

        private FixtureTransport() throws IOException {
            String prefix = "fixtures/energy-reference-wasserburg-am-inn-2024/";
            manifest = Files.readAllBytes(EnergyFixture.kitPath(prefix + "manifest.json"));
            for (var descriptor : MAPPER.readTree(manifest).path("artifacts")) {
                String id = descriptor.path("artifactId").asText();
                artifacts.put(id, Files.readAllBytes(EnergyFixture.kitPath(prefix + "artifacts/" + id + ".json")));
            }
        }

        @Override
        public EnergyPublicationClient.Response get(URI uri, String token, String etag, int maxBytes) throws IOException {
            calls++;
            assertEquals("https://odip.example", uri.getScheme() + "://" + uri.getHost());
            if (!token.equals("fixture-token") && !token.equals("token")) {
                throw new AssertionError("unexpected bearer token");
            }
            if (etag != null) conditionalCalls++;
            if (forceStatus != 0) return new EnergyPublicationClient.Response(forceStatus, null, null);
            if (etag != null) return new EnergyPublicationClient.Response(304, etag, null);
            if (uri.getPath().equals(PREFIX)) return new EnergyPublicationClient.Response(200, etag(manifest), manifest);
            String artifactPrefix = PREFIX + "/artifacts/";
            if (!uri.getPath().startsWith(artifactPrefix)) throw new IOException("unexpected URL");
            String id = uri.getPath().substring(artifactPrefix.length());
            byte[] bytes = artifacts.get(id);
            if (bytes == null || bytes.length > maxBytes) throw new IOException("missing or oversized artifact");
            return new EnergyPublicationClient.Response(200, etag(bytes), bytes);
        }

        private static String etag(byte[] bytes) throws IOException {
            try {
                return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + "\"";
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
        }
    }
}
