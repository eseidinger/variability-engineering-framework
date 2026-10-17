package de.eseidinger.variabilityengineering.http;

import java.util.Map;

public class FixtureAiProfile extends FixtureImportProfile {
    @Override public Map<String, String> getConfigOverrides() {
        return Map.of("energy.ai.enabled", "true", "quarkus.langchain4j.openai.enable-integration", "true");
    }
}
