package de.eseidinger.variabilityengineering.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.HttpException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Browser-session proposal route. It never reserves or starts an optimization run. */
@Path("/api/v1/ai/request-proposals")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EnergyAiProposalResource {
    private static final Logger LOG = Logger.getLogger(EnergyAiProposalResource.class);
    @Inject ProposalService proposals;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name = "energy.ai.enabled", defaultValue = "false") boolean enabled;

    @POST
    public Response propose(@Context HttpHeaders headers, String body) {
        if (EnergyOptimizationResource.principal(headers) == null) {
            return error(401, "invalid_principal", "Normalized platform identity is required.");
        }
        if (!enabled) return error(503, "ai_unavailable", "AI proposals are not available now.");
        if (body == null) return error(400, "invalid_json", "A valid JSON document is required.");
        if (body.getBytes(StandardCharsets.UTF_8).length > 8192) {
            return error(413, "request_too_large", "Proposal input must be at most 8 KiB.");
        }
        try {
            var input = mapper.readTree(body);
            if (input == null || !input.isObject() || input.size() != 1
                    || !input.path("description").isTextual()
                    || input.path("description").textValue().isBlank()
                    || input.path("description").textValue().length() > 4000) {
                return error(400, "invalid_request", "A nonempty description is required.");
            }
            return Response.ok(proposals.propose(input.path("description").textValue())).build();
        } catch (JsonProcessingException exception) {
            return error(400, "invalid_json", "A valid JSON document is required.");
        } catch (ProposalService.InvalidProposal exception) {
            LOG.warnf("Rejected AI proposal: %s", exception.reason());
            return error(502, "invalid_ai_proposal",
                    "The AI proposal could not be validated (" + exception.reason() + ").");
        } catch (RuntimeException exception) {
            LOG.warnf("AI proposal provider failed: %s", failureSummary(exception));
            return error(503, "ai_unavailable", "AI proposals are not available now.");
        }
    }

    private static String failureSummary(Throwable failure) {
        StringBuilder summary = new StringBuilder();
        for (int depth = 0; failure != null && depth < 6; depth++, failure = failure.getCause()) {
            if (depth > 0) summary.append(" caused by ");
            summary.append(failure.getClass().getName());
            if (failure instanceof HttpException http) summary.append(" (HTTP ").append(http.statusCode()).append(')');
        }
        return summary.toString();
    }

    private static Response error(int status, String code, String message) {
        return Response.status(status).entity(Map.of("schemaVersion", "error/v1", "status", status,
                "code", code, "message", message)).build();
    }
}
