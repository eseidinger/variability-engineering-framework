package de.eseidinger.variabilityengineering.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.eseidinger.variabilityengineering.energy.EnergyOptimizationRequest;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyRunRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Builds the only evidence that the explanation model may see. */
@ApplicationScoped
public class EnergyEvidenceService {
    @Inject ObjectMapper mapper;
    @Inject JdbcEnergyRunRepository runs;

    public ObjectNode prepare(JdbcEnergyRunRepository.Run run) {
        if (!Set.of("SUCCEEDED", "INFEASIBLE").contains(run.status()) || run.resultJson() == null) {
            throw new EvidenceNotReady();
        }
        try {
            JsonNode request = mapper.readTree(run.requestJson());
            JsonNode result = mapper.readTree(run.resultJson());
            if (!EnergyOptimizationResource.requestShape(request) || !resultShape(result)) throw new InvalidEvidence();
            EnergyOptimizationRequest typed = mapper.treeToValue(request, EnergyOptimizationRequest.class);
            if (!run.datasetId().equals(typed.dataset().datasetId())
                    || !run.version().equals(typed.dataset().version())
                    || !result.path("runId").asText().equals(run.runId())
                    || !result.path("status").asText().equals(run.status())
                    || !result.path("schemaVersion").asText().equals("energy-optimization-result/v1")
                    || !result.path("dataset").path("datasetId").asText().equals(run.datasetId())
                    || !result.path("dataset").path("version").asText().equals(run.version())
                    || !result.path("dataset").path("manifestSha256").asText().equals(run.manifestSha256())
                    || !result.path("modelVersion").isTextual()
                    || !result.path("simulationVersion").isTextual()
                    || !result.path("optimalityStatus").isTextual()
                    || !result.has("recommendedConfigurationId")) throw new InvalidEvidence();
            var sources = mapper.createObjectNode();
            var customer = ((ObjectNode) request).deepCopy();
            customer.remove("assumptions");
            source(sources, "request", "CUSTOMER_INPUT", customer);
            source(sources, "assumptions", "MODEL_ASSUMPTION", request.path("assumptions"));
            source(sources, "result", "CALCULATED_OUTPUT", result);
            if ("SUCCEEDED".equals(run.status())) {
                String recommendation = result.path("recommendedConfigurationId").asText();
                String candidateJson = runs.recommendedCandidate(run.runId(), recommendation)
                        .orElseThrow(InvalidEvidence::new);
                JsonNode candidate = mapper.readTree(candidateJson);
                if (!candidateShape(candidate)
                        || !candidate.path("schemaVersion").asText().equals("energy-candidate/v1")
                        || !candidate.path("configurationId").asText().equals(recommendation)
                        || !candidate.path("feasible").isBoolean() || !candidate.path("feasible").booleanValue()
                        || !candidate.path("metrics").path("annualizedCostEUR").isNumber()
                        || !candidate.path("metrics").path("selfSufficiency").isNumber()
                        || !candidate.path("constraints").isArray()
                        || candidate.path("constraints").isEmpty()) throw new InvalidEvidence();
                source(sources, "candidate", "CALCULATED_OUTPUT", candidate);
            } else if (!result.path("recommendedConfigurationId").isNull()
                    || !"NOT_APPLICABLE".equals(result.path("optimalityStatus").asText())) {
                throw new InvalidEvidence();
            }
            requireBounds(sources, mapper);
            return sources;
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException exception) {
            throw new InvalidEvidence();
        }
    }

    private void source(ObjectNode sources, String id, String category, JsonNode document) {
        var item = mapper.createObjectNode();
        item.put("category", category);
        item.set("document", document);
        sources.set(id, item);
    }

    static void requireBounds(ObjectNode sources, ObjectMapper mapper) throws JsonProcessingException {
        // Pretty serialization is a conservative bound relative to Python's default JSON spacing.
        if (sources.size() > 16 || mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sources)
                .getBytes(StandardCharsets.UTF_8).length > 65536) throw new EvidenceTooLarge();
    }

    private static boolean resultShape(JsonNode result) {
        if (!object(result, Set.of("schemaVersion", "runId", "status", "dataset", "modelVersion",
                "simulationVersion", "optimizerVersion", "optimalityStatus", "evaluatedCandidateCount",
                "feasibleCandidateCount", "recommendedConfigurationId", "warnings", "createdAt", "completedAt"))
                || !object(result.path("dataset"), Set.of("datasetId", "version", "schemaRef", "manifestSha256"))
                || !result.path("runId").isTextual() || !result.path("status").isTextual()
                || !result.path("modelVersion").isTextual() || !result.path("simulationVersion").isTextual()
                || !result.path("optimizerVersion").isTextual() || !result.path("optimalityStatus").isTextual()
                || !result.path("evaluatedCandidateCount").isIntegralNumber()
                || !result.path("feasibleCandidateCount").isIntegralNumber()
                || !result.path("warnings").isArray() || !result.path("createdAt").isTextual()
                || !result.path("completedAt").isTextual()) return false;
        boolean success = "SUCCEEDED".equals(result.path("status").asText());
        return success ? result.path("recommendedConfigurationId").isTextual()
                && result.path("feasibleCandidateCount").intValue() > 0
                && Set.of("PROVEN_OPTIMAL", "FEASIBLE_NOT_PROVEN")
                    .contains(result.path("optimalityStatus").asText())
                : "INFEASIBLE".equals(result.path("status").asText())
                && result.path("recommendedConfigurationId").isNull()
                && result.path("feasibleCandidateCount").intValue() == 0
                && "NOT_APPLICABLE".equals(result.path("optimalityStatus").asText());
    }

    private static boolean candidateShape(JsonNode candidate) {
        if (!object(candidate, Set.of("schemaVersion", "configurationId", "components", "feasible",
                "constraints", "metrics")) || !candidate.path("configurationId").isTextual()
                || !candidate.path("components").isArray()
                || candidate.path("components").size() < 2 || candidate.path("components").size() > 3
                || !candidate.path("feasible").isBoolean()
                || !candidate.path("constraints").isArray() || candidate.path("constraints").isEmpty()
                || !object(candidate.path("metrics"), Set.of("initialInvestmentEUR", "annualizedCostEUR",
                    "selfSufficiency", "annualDemandKWh", "gridImportKWh", "gridExportKWh",
                    "onSiteSolarUseKWh", "unmetDemandKWh", "inverterClippingKWh",
                    "terminalDiscardedEnergyKWh"))) return false;
        for (JsonNode component : candidate.path("components")) {
            if (!object(component, Set.of("componentId", "type", "quantity", "existing"))
                    || !component.path("componentId").isTextual() || !component.path("type").isTextual()
                    || !component.path("quantity").isIntegralNumber() || !component.path("existing").isBoolean()) return false;
        }
        for (JsonNode constraint : candidate.path("constraints")) {
            if (!constraint.isObject() || !constraint.path("code").isTextual()
                    || !Set.of("PASS", "FAIL").contains(constraint.path("status").asText())
                    || !constraint.has("actual")) return false;
        }
        for (JsonNode metric : candidate.path("metrics")) if (!metric.isNumber()) return false;
        return true;
    }

    private static boolean object(JsonNode node, Set<String> fields) {
        if (!node.isObject() || node.size() != fields.size()) return false;
        for (String field : fields) if (!node.has(field)) return false;
        return true;
    }

    public static final class EvidenceNotReady extends RuntimeException { }
    public static final class InvalidEvidence extends RuntimeException { }
    public static final class EvidenceTooLarge extends RuntimeException { }
}
