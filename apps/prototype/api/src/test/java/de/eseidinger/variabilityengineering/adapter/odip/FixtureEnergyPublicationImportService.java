package de.eseidinger.variabilityengineering.adapter.odip;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyPublicationRepository;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Test-only ODIP boundary backed by the immutable released fixture. */
@Alternative
@Dependent
public class FixtureEnergyPublicationImportService extends EnergyPublicationImportService {
    private final JdbcEnergyPublicationRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public FixtureEnergyPublicationImportService(JdbcEnergyPublicationRepository repository) {
        super(repository, (EnergyPublicationClient) null);
        this.repository = repository;
    }

    @Override
    public VerifiedEnergyPublication importExact(String datasetId, String version) throws IOException {
        if (!"energy-reference-wasserburg-am-inn-2024".equals(datasetId) || !"1.0.0".equals(version)) {
            throw new IOException("fixture publication was not found");
        }
        Path fixture = Path.of(System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY"),
                "fixtures/energy-reference-wasserburg-am-inn-2024");
        byte[] manifest = Files.readAllBytes(fixture.resolve("manifest.json"));
        var artifacts = new HashMap<String, byte[]>();
        for (var descriptor : mapper.readTree(manifest).path("artifacts")) {
            String id = descriptor.path("artifactId").asText();
            artifacts.put(id, Files.readAllBytes(fixture.resolve("artifacts/" + id + ".json")));
        }
        var verified = new EnergyPublicationVerifier().verify(manifest, artifacts);
        return repository.saveIfAbsent(new EnergyPublicationClient.Cached(verified, null, Map.of())).publication();
    }
}
