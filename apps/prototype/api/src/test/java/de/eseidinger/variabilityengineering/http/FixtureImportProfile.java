package de.eseidinger.variabilityengineering.http;

import de.eseidinger.variabilityengineering.adapter.odip.FixtureEnergyPublicationImportService;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Set;

public class FixtureImportProfile implements QuarkusTestProfile {
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(FixtureEnergyPublicationImportService.class);
    }
}
