package de.eseidinger.variabilityengineering.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.eseidinger.variabilityengineering.energy.ai.ExplanationModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Checks every model citation against VEF's immutable, approved snapshot. */
@ApplicationScoped
public class ExplanationService {
    @Inject ExplanationModel model;
    @Inject ObjectMapper mapper;

    public JsonNode explain(JsonNode sources) {
        String raw;
        try {
            raw = model.explain(mapper.writeValueAsString(sources));
        } catch (JsonProcessingException exception) {
            throw new InvalidExplanation("evidence_serialization_failed");
        }
        return validate(raw, sources);
    }

    JsonNode validate(String raw, JsonNode sources) {
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > 131072) {
            throw new InvalidExplanation("empty_or_oversized_output");
        }
        try {
            JsonNode explanation = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw);
            JsonNode result = sources.path("result").path("document");
            if (!object(explanation, Set.of("schemaVersion", "runId", "claims", "missingEvidence"))
                    || !"energy-ai-explanation/v1".equals(explanation.path("schemaVersion").asText())
                    || !result.path("runId").asText().equals(explanation.path("runId").asText())
                    || !explanation.path("claims").isArray()
                    || explanation.path("claims").isEmpty() || explanation.path("claims").size() > 64
                    || !strings(explanation.path("missingEvidence"), 32, 2000)) throw new InvalidExplanation("invalid_envelope");
            var required = new HashSet<Cited>();
            for (String pointer : new String[]{"/runId", "/dataset/datasetId", "/dataset/version",
                    "/modelVersion", "/simulationVersion", "/recommendedConfigurationId", "/optimalityStatus"}) {
                required.add(new Cited("result", pointer));
            }
            if ("SUCCEEDED".equals(result.path("status").asText())) {
                JsonNode candidate = sources.path("candidate").path("document");
                if (!candidate.isObject() || !candidate.path("configurationId").asText()
                        .equals(result.path("recommendedConfigurationId").asText())
                        || !candidate.path("feasible").asBoolean(false)) throw new InvalidExplanation("invalid_recommendation_evidence");
                required.add(new Cited("candidate", "/metrics/annualizedCostEUR"));
                required.add(new Cited("candidate", "/metrics/selfSufficiency"));
                for (int i = 0; i < candidate.path("constraints").size(); i++) {
                    required.add(new Cited("candidate", "/constraints/" + i));
                }
            } else if ("INFEASIBLE".equals(result.path("status").asText())) {
                if (!result.path("recommendedConfigurationId").isNull()
                        || explanation.path("missingEvidence").isEmpty()) throw new InvalidExplanation("invalid_infeasible_explanation");
                required.add(new Cited("result", "/status"));
            } else throw new InvalidExplanation("unsupported_result_status");

            var cited = new HashSet<Cited>();
            for (JsonNode claim : explanation.path("claims")) {
                if (!object(claim, Set.of("text", "category", "citations"))
                        || !shortText(claim.path("text"), 2000)
                        || !Set.of("MEASURED_SOURCE_DATA", "DERIVED_REFERENCE_PROFILE", "CUSTOMER_INPUT",
                                "MODEL_ASSUMPTION", "CALCULATED_OUTPUT").contains(claim.path("category").asText())
                        || !claim.path("citations").isArray() || claim.path("citations").isEmpty()
                        || claim.path("citations").size() > 16) throw new InvalidExplanation("invalid_claim");
                for (JsonNode citation : claim.path("citations")) {
                    if (!object(citation, Set.of("source", "pointer", "value"))) throw new InvalidExplanation("invalid_citation");
                    String sourceId = citation.path("source").asText();
                    String pointer = citation.path("pointer").asText();
                    if (!sourceId.matches("^[a-zA-Z][a-zA-Z0-9_-]{0,63}$")
                            || pointer.length() > 512
                            || !pointer.matches("^(?:/(?:[^~/]|~[01])*)*$")
                            || !sources.has(sourceId)
                            || !claim.path("category").asText().equals(sources.path(sourceId).path("category").asText())) {
                        throw new InvalidExplanation("invalid_citation_source_pointer_or_category");
                    }
                    JsonNode actual = resolve(sources.path(sourceId).path("document"), pointer);
                    if (actual.isMissingNode() || !sameValue(actual, citation.path("value"))) {
                        throw new InvalidExplanation("citation_value_mismatch");
                    }
                    cited.add(new Cited(sourceId, pointer));
                }
            }
            if (!cited.containsAll(required)) throw new InvalidExplanation("missing_required_citations");
            return explanation;
        } catch (JsonProcessingException exception) {
            throw new InvalidExplanation("invalid_json");
        }
    }

    private static boolean sameValue(JsonNode actual, JsonNode cited) {
        if (actual.isNumber() && cited.isNumber()) {
            return new BigDecimal(actual.asText()).compareTo(new BigDecimal(cited.asText())) == 0;
        }
        return actual.equals(cited);
    }

    private static JsonNode resolve(JsonNode document, String pointer) {
        JsonNode value = document;
        if (pointer.isEmpty()) return value;
        for (String part : pointer.substring(1).split("/", -1)) {
            String token = part.replace("~1", "/").replace("~0", "~");
            if (value.isArray()) {
                if (!token.matches("0|[1-9][0-9]*")) throw new InvalidExplanation("invalid_citation_pointer");
                try {
                    value = value.path(Integer.parseInt(token));
                } catch (NumberFormatException exception) {
                    throw new InvalidExplanation("invalid_citation_pointer");
                }
            } else if (value.isObject()) {
                value = value.path(token);
            } else throw new InvalidExplanation("invalid_citation_pointer");
            if (value.isMissingNode()) throw new InvalidExplanation("invalid_citation_pointer");
        }
        return value;
    }

    private static boolean object(JsonNode node, Set<String> keys) {
        if (node == null || !node.isObject() || node.size() != keys.size()) return false;
        for (String key : keys) if (!node.has(key)) return false;
        return true;
    }

    private static boolean shortText(JsonNode node, int max) {
        return node.isTextual() && !node.textValue().isBlank() && node.textValue().length() <= max;
    }

    private static boolean strings(JsonNode array, int maxCount, int maxLength) {
        if (!array.isArray() || array.size() > maxCount) return false;
        for (JsonNode item : array) if (!shortText(item, maxLength)) return false;
        return true;
    }

    private record Cited(String source, String pointer) { }
    public static final class InvalidExplanation extends RuntimeException {
        private final String reason;

        InvalidExplanation(String reason) { this.reason = reason; }

        public String reason() { return reason; }
    }
}
