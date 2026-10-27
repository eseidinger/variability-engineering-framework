package de.eseidinger.variabilityengineering.energy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in local compute measurement; excludes HTTP, persistence and platform limits. */
@EnabledIfEnvironmentVariable(named = "ENERGY_RUN_BENCHMARK", matches = "true")
class EnergyRuntimeBenchmarkTest {
    @Test
    @Timeout(330)
    void measuresFullReferenceYearAtTheExactCandidateLimit() throws Exception {
        assertTrue(Runtime.getRuntime().maxMemory() <= 512L * 1024 * 1024,
                "Use JAVA_TOOL_OPTIONS=-Xmx512m so the forked JVM has a measured heap bound");
        var publication = EnergyFixture.publication();
        var original = EnergyFixture.request();
        var request = new EnergyOptimizationRequest(original.schemaVersion(), original.dataset(),
                new EnergyOptimizationRequest.Site(original.site().regionCode(), 29,
                        original.site().annualDemandKWh(), original.site().existingComponentIds()),
                original.constraints(), original.objective(), original.assumptions());
        var components = new ArrayList<EnergyComponent>();
        for (int i = 0; i < 20; i++) {
            components.add(EnergyFeatureModelTest.pv("pv-model-" + i, 1, 1));
            components.add(EnergyFeatureModelTest.inverter("inv-model-" + i, 25));
        }
        for (int i = 0; i < 6; i++) components.add(EnergyFeatureModelTest.battery("bat-model-" + i, true));
        var catalog = new EnergyCatalog(EnergyCatalog.SCHEMA_VERSION, "1.0.0", "EUR", components);
        assertEquals(100_000, new EnergyFeatureModel().estimateCandidateEvaluations(request, catalog));
        ManagementFactory.getMemoryPoolMXBeans().forEach(pool -> pool.resetPeakUsage());
        long started = System.nanoTime();
        var result = new EnergyOptimizer().optimize(request, catalog, publication.profile());
        double seconds = (System.nanoTime() - started) / 1e9;
        assertEquals(100_000, result.candidates().size());
        assertEquals("PROVEN_OPTIMAL", result.optimalityStatus());
        var report = new ObjectMapper().createObjectNode();
        report.put("scope", "local-pure-optimizer-synthetic-catalog");
        report.put("releaseAccepted", false);
        report.put("candidateEvaluations", result.candidates().size());
        report.put("profileHours", 8784);
        report.put("elapsedSeconds", seconds);
        report.put("heapLimitBytes", Runtime.getRuntime().maxMemory());
        report.put("sumOfHeapPoolPeaksBytes", ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP).mapToLong(pool -> pool.getPeakUsage().getUsed()).sum());
        report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        report.put("javaVersion", System.getProperty("java.version"));
        Path reportPath = Path.of("target/energy-runtime-benchmark.json");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report.toPrettyString() + "\n");
        assertTrue(seconds < 300, "Local compute exceeded the worker deadline; review deployment bounds");
    }
}
