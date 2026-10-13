package de.eseidinger.variabilityengineering.adapter.odip;

import de.eseidinger.variabilityengineering.persistence.JdbcEnergyPublicationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/** Imports an exact ODIP publication only after a fresh authorized read and byte verification. */
@ApplicationScoped
public class EnergyPublicationImportService {
    private final JdbcEnergyPublicationRepository repository;
    private final EnergyPublicationClient client;

    @Inject
    public EnergyPublicationImportService(JdbcEnergyPublicationRepository repository,
            @ConfigProperty(name = "energy.odip.origin") String origin,
            @ConfigProperty(name = "energy.odip.binding-prefix") Optional<String> bindingPrefix) {
        this.repository = repository;
        this.client = bindingPrefix.filter(prefix -> !prefix.isBlank())
                .map(prefix -> new EnergyPublicationClient(URI.create(origin),
                        new ManagedClientCredentialsTokenProvider(prefix, System.getenv()),
                        new EnergyPublicationClient.JdkTransport()))
                .orElse(null);
    }

    EnergyPublicationImportService(JdbcEnergyPublicationRepository repository, EnergyPublicationClient client) {
        this.repository = repository;
        this.client = client;
    }

    public VerifiedEnergyPublication importExact(String datasetId, String version)
            throws IOException, InterruptedException {
        if (client == null) {
            throw new IllegalStateException("VEF ODIP managed reader binding is not configured");
        }
        var previous = repository.find(datasetId, version).orElse(null);
        var verified = client.fetchExact(datasetId, version, previous);
        return repository.saveIfAbsent(verified).publication();
    }
}
