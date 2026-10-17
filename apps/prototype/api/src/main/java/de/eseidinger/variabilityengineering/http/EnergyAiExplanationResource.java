package de.eseidinger.variabilityengineering.http;

import de.eseidinger.variabilityengineering.persistence.JdbcEnergyRunRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

/** Generates an explanation only from a completed run owned by the caller. */
@Path("/api/v1/ai/optimization-runs/{runId}/explanation")
@Produces(MediaType.APPLICATION_JSON)
public class EnergyAiExplanationResource {
    @Inject JdbcEnergyRunRepository runs;
    @Inject EnergyEvidenceService evidence;
    @Inject ExplanationService explanations;
    @ConfigProperty(name = "energy.ai.enabled", defaultValue = "false") boolean enabled;

    @POST
    public Response explain(@Context HttpHeaders headers, @PathParam("runId") String runId) {
        String principal = EnergyOptimizationResource.principal(headers);
        if (principal == null) return error(401, "invalid_principal", "Normalized platform identity is required.");
        if (runId == null || !runId.matches("^[a-zA-Z0-9][a-zA-Z0-9_-]{7,127}$")) {
            return error(400, "invalid_run_id", "Run ID is malformed.");
        }
        var owned = runs.findOwned(runId, principal);
        if (owned.isEmpty()) return error(404, "run_not_found", "Run was not found.");
        if (!enabled) return error(503, "ai_unavailable", "AI explanations are not available now.");
        try {
            return Response.ok(explanations.explain(evidence.prepare(owned.get()))).build();
        } catch (EnergyEvidenceService.EvidenceNotReady exception) {
            return error(409, "run_not_complete", "The run has not completed.");
        } catch (EnergyEvidenceService.EvidenceTooLarge | EnergyEvidenceService.InvalidEvidence exception) {
            return error(422, "evidence_unavailable", "Approved run evidence is unavailable or exceeds its limit.");
        } catch (ExplanationService.InvalidExplanation exception) {
            return error(502, "invalid_ai_explanation", "The AI explanation could not be validated.");
        } catch (RuntimeException exception) {
            return error(503, "ai_unavailable", "AI explanations are not available now.");
        }
    }

    private static Response error(int status, String code, String message) {
        return Response.status(status).entity(Map.of("schemaVersion", "error/v1", "status", status,
                "code", code, "message", message)).build();
    }
}
