package de.eseidinger.variabilityengineering.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationClient;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationImportService;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationVerifier;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class JdbcEnergyPublicationRepositoryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ID = "energy-reference-wasserburg-am-inn-2024";
    private static final String VERSION = "1.0.0";

    @Inject
    JdbcEnergyPublicationRepository repository;

    @Inject
    EnergyPublicationImportService importer;

    @Test
    void preservesVerifiedBytesAndRejectsAnImmutableVersionChange() throws IOException {
        if (System.getenv("ENERGY_ODIP_BINDING_PREFIX") == null) {
            assertThrows(IllegalStateException.class, () -> importer.importExact(ID, VERSION));
        }
        var input = fixture();
        var publication = new EnergyPublicationVerifier().verify(input.manifest(), input.artifacts());
        var cached = new EnergyPublicationClient.Cached(publication, "manifest-etag", java.util.Map.of("pv-yield", "pv-etag"));
        var stored = repository.saveIfAbsent(cached);
        var loaded = repository.find(ID, VERSION).orElseThrow();
        assertEquals(stored.publication().manifestSha256(), loaded.publication().manifestSha256());
        assertEquals("manifest-etag", loaded.manifestEtag());
        assertEquals("pv-etag", loaded.artifactEtags().get("pv-yield"));
        assertArrayEquals(input.manifest(), loaded.publication().manifestBytes());
        for (var id : input.artifacts().keySet()) {
            assertArrayEquals(input.artifacts().get(id), loaded.publication().artifactBytes().get(id));
        }
        assertEquals(8_784, loaded.publication().profile().hours().size());
        assertEquals(loaded.publication().manifestSha256(), repository.saveIfAbsent(cached).publication().manifestSha256());

        var changed = (ObjectNode) MAPPER.readTree(input.manifest());
        ((ObjectNode) changed.path("quality").path("warnings").get(0)).put("message", "Different valid warning");
        var different = new EnergyPublicationVerifier().verify(MAPPER.writeValueAsBytes(changed), input.artifacts());
        assertThrows(IllegalStateException.class, () -> repository.saveIfAbsent(
                new EnergyPublicationClient.Cached(different, null, java.util.Map.of())));
        assertArrayEquals(input.manifest(), repository.find(ID, VERSION).orElseThrow().publication().manifestBytes());
        assertTrue(repository.find("missing-energy-publication", VERSION).isEmpty());
    }

    private static Fixture fixture() throws IOException {
        String root = System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY");
        if (root == null || root.isBlank()) throw new IllegalStateException("ENERGY_CONTRACT_KIT_DIRECTORY required");
        Path prefix = Path.of(root, "fixtures/energy-reference-wasserburg-am-inn-2024");
        byte[] manifest = Files.readAllBytes(prefix.resolve("manifest.json"));
        var artifacts = new HashMap<String, byte[]>();
        for (var descriptor : MAPPER.readTree(manifest).path("artifacts")) {
            String id = descriptor.path("artifactId").asText();
            artifacts.put(id, Files.readAllBytes(prefix.resolve("artifacts/" + id + ".json")));
        }
        return new Fixture(manifest, artifacts);
    }

    private record Fixture(byte[] manifest, HashMap<String, byte[]> artifacts) { }
}
