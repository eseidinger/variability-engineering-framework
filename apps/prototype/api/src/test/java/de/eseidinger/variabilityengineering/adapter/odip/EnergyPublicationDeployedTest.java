package de.eseidinger.variabilityengineering.adapter.odip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Opt-in cross-project check; run with VEF's managed reader binding, never an ODIP operator credential. */
@EnabledIfEnvironmentVariable(named = "ENERGY_ODIP_BINDING_PREFIX", matches = "[A-Z][A-Z0-9_]*")
class EnergyPublicationDeployedTest {
    @Test
    void readsGrantedEnergyPublicationButNotNonEnergyControl() throws Exception {
        String origin = System.getenv().getOrDefault("ENERGY_ODIP_ORIGIN", "https://web-odip.apps.eseidinger.de");
        var client = new EnergyPublicationClient(URI.create(origin),
                new ManagedClientCredentialsTokenProvider(System.getenv("ENERGY_ODIP_BINDING_PREFIX"), System.getenv()),
                new EnergyPublicationClient.JdkTransport());

        String dataset = "energy-reference-wasserburg-am-inn-2024";
        String version = "1.0.0";
        var imported = client.fetchExact(dataset, version, null);
        assertEquals("252b90b18b594a764a2744ba133a8db56b233e9e375301befc7616e9c18c1715",
                imported.publication().manifestSha256());
        assertEquals(imported.publication().manifestSha256(),
                client.fetchExact(dataset, version, imported).publication().manifestSha256());

        IOException denied = assertThrows(IOException.class,
                () -> client.fetchExact("non-energy-mobility-counts-2024", version, null));
        assertEquals("ODIP returned HTTP 403", denied.getMessage());
    }
}
