package de.eseidinger.variabilityengineering.adapter.odip;

import de.eseidinger.variabilityengineering.persistence.JdbcEnergyPublicationRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnergyPublicationImportServiceTest {
    private static final String ID = "energy-reference-wasserburg-am-inn-2024";
    private static final String VERSION = "1.0.0";

    @Test
    void importsOnceAndRequiresFreshAuthorizationEvenWhenCached() throws Exception {
        var repository = new MemoryRepository();
        var transport = new FixtureTransport();
        var client = new EnergyPublicationClient(URI.create("https://odip.example"), () -> "reader-token", transport);
        var service = new EnergyPublicationImportService(repository, client);

        assertEquals("252b90b18b594a764a2744ba133a8db56b233e9e375301befc7616e9c18c1715",
                service.importExact(ID, VERSION).manifestSha256());
        assertEquals(1, repository.saves);
        assertEquals(6, transport.calls);

        service.importExact(ID, VERSION);
        assertEquals(2, repository.saves);
        assertEquals(6, transport.conditionalCalls);

        transport.denied = true;
        assertThrows(IOException.class, () -> service.importExact(ID, VERSION));
        assertEquals(2, repository.saves);
        assertEquals(13, transport.calls);
    }

    private static final class MemoryRepository extends JdbcEnergyPublicationRepository {
        private EnergyPublicationClient.Cached stored;
        private int saves;

        @Override
        public Optional<EnergyPublicationClient.Cached> find(String datasetId, String version) {
            return Optional.ofNullable(stored);
        }

        @Override
        public EnergyPublicationClient.Cached saveIfAbsent(EnergyPublicationClient.Cached candidate) {
            saves++;
            if (stored == null) stored = candidate;
            return stored;
        }
    }

    private static final class FixtureTransport implements EnergyPublicationClient.Transport {
        private final byte[] manifest;
        private final Map<String, byte[]> artifacts = new HashMap<>();
        private int calls;
        private int conditionalCalls;
        private boolean denied;

        private FixtureTransport() throws IOException {
            String kit = System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY");
            if (kit == null || kit.isBlank()) throw new IllegalStateException("ENERGY_CONTRACT_KIT_DIRECTORY required");
            Path fixture = Path.of(kit, "fixtures", ID);
            manifest = Files.readAllBytes(fixture.resolve("manifest.json"));
            var document = new com.fasterxml.jackson.databind.ObjectMapper().readTree(manifest);
            for (var descriptor : document.path("artifacts")) {
                String id = descriptor.path("artifactId").asText();
                artifacts.put(id, Files.readAllBytes(fixture.resolve("artifacts/" + id + ".json")));
            }
        }

        @Override
        public EnergyPublicationClient.Response get(URI uri, String token, String etag, int maxBytes) throws IOException {
            calls++;
            assertEquals("reader-token", token);
            if (denied) return new EnergyPublicationClient.Response(403, null, null);
            if (etag != null) {
                conditionalCalls++;
                return new EnergyPublicationClient.Response(304, etag, null);
            }
            String path = uri.getPath();
            String root = "/api/v1/datasets/" + ID + "/versions/" + VERSION;
            byte[] bytes = path.equals(root) ? manifest : artifacts.get(path.substring((root + "/artifacts/").length()));
            if (bytes == null || bytes.length > maxBytes) throw new IOException("unexpected publication artifact");
            try {
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                return new EnergyPublicationClient.Response(200, "\"" + digest + "\"", bytes);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
        }
    }
}
