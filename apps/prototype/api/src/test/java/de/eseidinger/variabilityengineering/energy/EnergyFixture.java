package de.eseidinger.variabilityengineering.energy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;

/** Maps already-validated released test inputs; production publication parsing belongs to VEF-01. */
final class EnergyFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EnergyFixture() {
    }

    static JsonNode document(String relativePath) throws IOException {
        String directory = System.getenv("ENERGY_CONTRACT_KIT_DIRECTORY");
        if (directory == null || directory.isBlank()) {
            throw new IllegalStateException("Prepare the pinned kit and set ENERGY_CONTRACT_KIT_DIRECTORY; see contracts/energy/README.md");
        }
        return MAPPER.readTree(Path.of(directory).resolve(relativePath).toFile());
    }

    static EnergyCatalog catalog() throws IOException {
        var document = document("fixtures/energy-reference-wasserburg-am-inn-2024/artifacts/component-catalog.json");
        var components = new ArrayList<EnergyComponent>();
        for (var node : document.path("components")) {
            var source = node.path("source");
            var metadata = new EnergyComponent.Metadata(node.path("componentId").asText(),
                    node.path("displayName").asText(), node.path("unitPriceEUR").asDouble(),
                    node.path("available").asBoolean(),
                    new EnergyComponent.Source(source.path("owner").asText(), URI.create(source.path("reference").asText()),
                            source.has("version") ? source.path("version").asText() : null,
                            source.path("license").asText(), Instant.parse(source.path("retrievedAt").asText())));
            components.add(switch (node.path("type").asText()) {
                case "PV_MODULE" -> new EnergyComponent.PvModule(metadata,
                        node.path("ratedPowerKWp").asDouble(), node.path("areaM2").asDouble());
                case "INVERTER" -> new EnergyComponent.Inverter(metadata,
                        node.path("acPowerKW").asDouble(), node.path("efficiencyFraction").asDouble());
                case "BATTERY" -> new EnergyComponent.Battery(metadata, node.path("usableCapacityKWh").asDouble(),
                        node.path("maxChargePowerKW").asDouble(), node.path("maxDischargePowerKW").asDouble(),
                        node.path("roundTripEfficiencyFraction").asDouble());
                default -> throw new IllegalArgumentException("unsupported fixture component type");
            });
        }
        return new EnergyCatalog(document.path("schemaVersion").asText(), document.path("catalogVersion").asText(),
                document.path("currency").asText(), components);
    }

    static EnergyOptimizationRequest request() throws IOException {
        var node = document("examples/optimization-request.json");
        var dataset = node.path("dataset");
        var site = node.path("site");
        var constraints = node.path("constraints");
        var assumptions = node.path("assumptions");
        var existingIds = new ArrayList<String>();
        site.path("existingComponentIds").forEach(id -> existingIds.add(id.asText()));
        return new EnergyOptimizationRequest(node.path("schemaVersion").asText(),
                new EnergyOptimizationRequest.DatasetReference(dataset.path("datasetId").asText(),
                        dataset.path("version").asText(), dataset.path("schemaRef").asText()),
                new EnergyOptimizationRequest.Site(site.path("regionCode").asText(), site.path("usableRoofAreaM2").asDouble(),
                        site.path("annualDemandKWh").asDouble(), existingIds),
                new EnergyOptimizationRequest.Constraints(constraints.path("maxInitialInvestmentEUR").asDouble(),
                        constraints.path("minSelfSufficiency").asDouble()),
                node.path("objective").asText(), new EnergyOptimizationRequest.Assumptions(
                        assumptions.path("analysisYears").asInt(), assumptions.path("discountRate").asDouble()));
    }
}
