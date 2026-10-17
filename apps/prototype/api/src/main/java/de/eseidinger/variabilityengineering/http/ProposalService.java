package de.eseidinger.variabilityengineering.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.eseidinger.variabilityengineering.energy.EnergyOptimizationRequest;
import de.eseidinger.variabilityengineering.energy.ai.ProposalModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

/** Validates the model's proposal before it can be shown to a consultant. */
@ApplicationScoped
public class ProposalService {
    @Inject ProposalModel model;
    @Inject ObjectMapper mapper;

    public JsonNode propose(String description) {
        String output = model.propose(description);
        if (output == null || output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536) {
            throw new InvalidProposal("empty_or_oversized_output");
        }
        JsonNode root;
        try {
            root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(output);
        } catch (JsonProcessingException error) {
            throw new InvalidProposal("invalid_json");
        }
        if (!object(root, Set.of("schemaVersion", "status", "requiresConfirmation", "request",
                "clarificationQuestions", "assumptionsToReview"))
                || !"energy-ai-request-proposal/v1".equals(root.path("schemaVersion").textValue())
                || !root.path("requiresConfirmation").isBoolean()
                || !root.path("requiresConfirmation").booleanValue()
                || !strings(root.path("clarificationQuestions"))
                || !strings(root.path("assumptionsToReview"))) throw new InvalidProposal("invalid_envelope");
        String status = root.path("status").textValue();
        if ("NEEDS_CLARIFICATION".equals(status)) {
            if (!root.path("request").isNull() || root.path("clarificationQuestions").isEmpty()) {
                throw new InvalidProposal("invalid_clarification");
            }
        } else if ("READY_FOR_REVIEW".equals(status)) {
            JsonNode request = root.path("request");
            if (!root.path("clarificationQuestions").isEmpty()
                    || root.path("assumptionsToReview").isEmpty()
                    || !EnergyOptimizationResource.requestShape(request)) {
                throw new InvalidProposal("invalid_ready_shape");
            }
            try {
                EnergyOptimizationRequest typed = mapper.treeToValue(request, EnergyOptimizationRequest.class);
                if (!"energy-reference-wasserburg-am-inn-2024".equals(typed.dataset().datasetId())
                        || !typed.site().existingComponentIds().isEmpty()) {
                    throw new InvalidProposal("unsupported_ready_request");
                }
            } catch (JsonProcessingException | IllegalArgumentException | NullPointerException error) {
                throw new InvalidProposal("invalid_ready_domain");
            }
        } else throw new InvalidProposal("invalid_status");
        return root;
    }

    private static boolean object(JsonNode node, Set<String> keys) {
        if (node == null || !node.isObject() || node.size() != keys.size()) return false;
        for (String key : keys) if (node.path(key).isMissingNode()) return false;
        return true;
    }

    private static boolean strings(JsonNode array) {
        if (!array.isArray() || array.size() > 20) return false;
        for (JsonNode item : array) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > 1000) return false;
        }
        return true;
    }

    public static final class InvalidProposal extends RuntimeException {
        private final String reason;

        InvalidProposal(String reason) { this.reason = reason; }

        public String reason() { return reason; }
    }
}
