package de.eseidinger.variabilityengineering.adapter.odip;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyPublicationRepository;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Explicit development-only import of the pinned release fixture; no ODIP credential is used. */
@Alternative
@Priority(1)
@Dependent
@IfBuildProfile("dev")
@IfBuildProperty(name = "energy.odip.local-fixture", stringValue = "true")
public class LocalFixtureEnergyPublicationImportService extends EnergyPublicationImportService {
    private final JdbcEnergyPublicationRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public LocalFixtureEnergyPublicationImportService(JdbcEnergyPublicationRepository repository) {
        super(repository, (EnergyPublicationClient) null);
        this.repository = repository;
    }

    @Override
    public VerifiedEnergyPublication importExact(String datasetId, String version) throws IOException {
        if (!"energy-reference-wasserburg-am-inn-2024".equals(datasetId) || !"1.0.0".equals(version)) {
            throw new IOException("local fixture publication was not found");
        }
        String directory = System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY");
        if (directory == null || directory.isBlank()) {
            throw new IllegalStateException("ENERGY_CONTRACT_KIT_DIRECTORY is required for local fixture import");
        }
        Path fixture = Path.of(directory, "fixtures/energy-reference-wasserburg-am-inn-2024");
        byte[] manifest = Files.readAllBytes(fixture.resolve("manifest.json"));
        var artifacts = new HashMap<String, byte[]>();
        for (var descriptor : mapper.readTree(manifest).path("artifacts")) {
            String id = descriptor.path("artifactId").asText();
            if (!id.matches("[a-z][a-z0-9-]{1,99}")) throw new IOException("invalid local fixture artifact ID");
            artifacts.put(id, Files.readAllBytes(fixture.resolve("artifacts/" + id + ".json")));
        }
        var verified = new EnergyPublicationVerifier().verify(manifest, artifacts);
        return repository.saveIfAbsent(new EnergyPublicationClient.Cached(verified, null, Map.of())).publication();
    }
}
