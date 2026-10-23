package de.eseidinger.variabilityengineering.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationClient;
import de.eseidinger.variabilityengineering.adapter.odip.EnergyPublicationImportService;
import de.eseidinger.variabilityengineering.energy.EnergyFeatureModel;
import de.eseidinger.variabilityengineering.energy.EnergyModelException;
import de.eseidinger.variabilityengineering.energy.EnergyOptimizationRequest;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyRunRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/** HTTP projection of the pinned optimization contract. Group membership is decided by the platform. */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public class EnergyOptimizationResource {
    private static final Logger LOG = Logger.getLogger(EnergyOptimizationResource.class);
    private static final String RUN_ID = "^[a-zA-Z0-9][a-zA-Z0-9_-]{7,127}$";
    private static final String PRINCIPAL_ID = "^prn_[0-9a-f]{32}$";
    private static final String GROUPS = "^grp_[0-9a-f]{32}( grp_[0-9a-f]{32})*$";

    @Inject JdbcEnergyRunRepository runs;
    @Inject EnergyPublicationImportService publications;
    @Inject ObjectMapper mapper;

    @GET
    @Path("/capabilities")
    public Response capabilities(@Context HttpHeaders headers) {
        if (principal(headers) == null) return error(401, "invalid_principal", "Normalized platform identity is required.");
        return Response.ok(Map.of(
                "maxModelsPerType", 20,
                "maxPvModules", 100,
                "inverterCount", 1,
                "maxBatteries", 4,
                "maxCandidateEvaluations", 100000,
                "runTimeoutSeconds", 300,
                "maxPageSize", 100)).build();
    }

    @POST
    @Path("/optimization-runs")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Context HttpHeaders headers, @HeaderParam("Idempotency-Key") String key,
                           String body) {
        String principal = principal(headers);
        if (principal == null) return error(401, "invalid_principal", "Normalized platform identity is required.");
        if (key == null || key.length() < 8 || key.length() > 128 || !key.matches("^[!-~]+$")) {
            return error(400, "invalid_idempotency_key", "Idempotency-Key must contain 8–128 visible characters.");
        }
        if (body == null || body.isBlank()) {
            return error(400, "invalid_json", "A valid optimization request JSON document is required.");
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > 65536) {
            return error(413, "request_too_large", "Optimization request must be at most 64 KiB.");
        }
        try {
            JsonNode tree = mapper.readTree(body);
            if (!requestShape(tree)) return error(400, "invalid_request", "Optimization request has missing or unknown fields.");
            var request = mapper.treeToValue(tree, EnergyOptimizationRequest.class);
            if (!request.site().existingComponentIds().isEmpty()) {
                return error(422, "existing_equipment_not_supported", "Existing equipment is not supported by this model version.");
            }
            String canonical = mapper.writeValueAsString(request);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
            var previous = runs.findByIdempotency(principal, key);
            if (previous.isPresent()) {
                if (!hash.equals(previous.get().requestHash())) {
                    return error(409, "idempotency_conflict", "This key was already used for a different request.");
                }
                return accepted(previous.get().run());
            }
            var publication = publications.importExact(request.dataset().datasetId(), request.dataset().version());
            new EnergyFeatureModel().estimateCandidateEvaluations(request, publication.catalog());
            var run = runs.reserve(principal, key, hash, canonical, request.dataset().datasetId(),
                    request.dataset().version(), publication.manifestSha256());
            return accepted(run);
        } catch (JdbcEnergyRunRepository.IdempotencyConflict exception) {
            return error(409, "idempotency_conflict", "This key was already used for a different request.");
        } catch (EnergyModelException exception) {
            return error(422, exception.code(), exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return error(422, "invalid_request", exception.getMessage());
        } catch (JsonProcessingException exception) {
            return error(400, "invalid_json", "A valid optimization request JSON document is required.");
        } catch (EnergyPublicationClient.HttpStatusException exception) {
            if (exception.status() == 404) {
                return error(422, "publication_not_found", "The requested publication version was not found.");
            }
            logPublicationFailure(exception);
            return error(503, "publication_unavailable", "The energy publication could not be verified now.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logPublicationFailure(exception);
            return error(503, "publication_unavailable", "The energy publication could not be verified now.");
        } catch (java.io.IOException | IllegalStateException | java.security.NoSuchAlgorithmException exception) {
            logPublicationFailure(exception);
            return error(503, "publication_unavailable", "The energy publication could not be verified now.");
        }
    }

    private static void logPublicationFailure(Throwable failure) {
        LOG.warnf("Energy publication unavailable while creating optimization run: %s", publicationFailureSummary(failure));
    }

    static String publicationFailureSummary(Throwable failure) {
        // Exception messages may contain URLs, response bodies, or credentials.
        StringBuilder summary = new StringBuilder();
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (depth > 0) summary.append(" caused by ");
            summary.append(failure.getClass().getName());
            if (failure instanceof EnergyPublicationClient.HttpStatusException http) {
                summary.append(" (HTTP ").append(http.status()).append(')');
            }
        }
        return summary.toString();
    }

    @GET
    @Path("/optimization-runs/{runId}")
    public Response run(@Context HttpHeaders headers, @PathParam("runId") String runId) {
        String principal = principal(headers);
        if (principal == null) return error(401, "invalid_principal", "Normalized platform identity is required.");
        if (!runId.matches(RUN_ID)) return error(400, "invalid_run_id", "Run ID is malformed.");
        var found = runs.findOwned(runId, principal);
        if (found.isEmpty()) return error(404, "run_not_found", "Run was not found.");
        var run = found.get();
        try {
            if (run.resultJson() != null) return Response.ok(mapper.readTree(run.resultJson())).build();
            var response = mapper.createObjectNode();
            response.put("runId", run.runId());
            response.put("status", run.status());
            response.put("createdAt", run.createdAt().toString());
            if (run.startedAt() != null && "RUNNING".equals(run.status())) {
                response.put("startedAt", run.startedAt().toString());
            }
            if ("FAILED".equals(run.status())) {
                response.put("completedAt", run.completedAt().toString());
                response.set("error", mapper.readTree(run.errorJson()));
            }
            return Response.ok(response).build();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored energy result is invalid", exception);
        }
    }

    @GET
    @Path("/optimization-runs/{runId}/candidates")
    public Response candidates(@Context HttpHeaders headers, @PathParam("runId") String runId,
                               @QueryParam("page") Integer page, @QueryParam("pageSize") Integer pageSize) {
        String principal = principal(headers);
        if (principal == null) return error(401, "invalid_principal", "Normalized platform identity is required.");
        if (!runId.matches(RUN_ID)) return error(400, "invalid_run_id", "Run ID is malformed.");
        int effectivePage = page == null ? 0 : page;
        int effectiveSize = pageSize == null ? 20 : pageSize;
        if (effectivePage < 0 || effectiveSize < 1 || effectiveSize > 100) {
            return error(400, "invalid_page", "Page must be nonnegative and pageSize must be 1–100.");
        }
        var found = runs.findOwned(runId, principal);
        if (found.isEmpty()) return error(404, "run_not_found", "Run was not found.");
        if (!Set.of("SUCCEEDED", "INFEASIBLE").contains(found.get().status())) {
            return error(409, "candidates_not_ready", "Candidate results are not available for this run.");
        }
        var stored = runs.candidates(runId, effectivePage, effectiveSize);
        var response = mapper.createObjectNode();
        var items = mapper.createArrayNode();
        for (var json : stored.items()) {
            try {
                items.add(mapper.readTree(json));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("stored energy candidate is invalid", exception);
            }
        }
        response.set("items", items);
        response.put("page", effectivePage);
        response.put("pageSize", effectiveSize);
        response.put("total", stored.total());
        return Response.ok(response).build();
    }

    static String principal(HttpHeaders headers) {
        String id = headers.getHeaderString("X-Platform-Principal-Id");
        String kind = headers.getHeaderString("X-Platform-Principal-Kind");
        String issuer = headers.getHeaderString("X-Platform-Principal-Issuer");
        String subject = headers.getHeaderString("X-Platform-Principal-Subject");
        String groups = headers.getHeaderString("X-Platform-Principal-Group-Ids");
        if (id == null || !id.matches(PRINCIPAL_ID)
                || !("user".equals(kind) || "service".equals(kind))
                || issuer == null || !issuer.matches("^https://[^ ]+$")
                || subject == null || !subject.matches("^[!-~]+$")
                || groups == null || !groups.matches(GROUPS)
                || ("service".equals(kind) && (headers.getHeaderString("X-Platform-Principal-Client-Id") == null
                || headers.getHeaderString("X-Platform-Principal-Client-Id").isBlank()))) return null;
        return id;
    }

    static boolean requestShape(JsonNode root) {
        return object(root, Set.of("schemaVersion", "dataset", "site", "constraints", "objective", "assumptions"))
                && object(root.path("dataset"), Set.of("datasetId", "version", "schemaRef"))
                && object(root.path("site"), Set.of("regionCode", "usableRoofAreaM2", "annualDemandKWh", "existingComponentIds"))
                && object(root.path("constraints"), Set.of("maxInitialInvestmentEUR", "minSelfSufficiency"))
                && object(root.path("assumptions"), Set.of("analysisYears", "discountRate"))
                && root.path("schemaVersion").isTextual() && root.path("objective").isTextual()
                && root.path("dataset").path("datasetId").isTextual()
                && root.path("dataset").path("version").isTextual()
                && root.path("dataset").path("schemaRef").isTextual()
                && root.path("site").path("regionCode").isTextual()
                && root.path("site").path("usableRoofAreaM2").isNumber()
                && root.path("site").path("annualDemandKWh").isNumber()
                && root.path("site").path("existingComponentIds").isArray()
                && arrayOfStrings(root.path("site").path("existingComponentIds"))
                && root.path("constraints").path("maxInitialInvestmentEUR").isNumber()
                && root.path("constraints").path("minSelfSufficiency").isNumber()
                && root.path("assumptions").path("analysisYears").isIntegralNumber()
                && root.path("assumptions").path("discountRate").isNumber();
    }

    private static boolean arrayOfStrings(JsonNode array) {
        for (JsonNode item : array) if (!item.isTextual()) return false;
        return true;
    }

    private static boolean object(JsonNode node, Set<String> keys) {
        if (node == null || !node.isObject() || node.size() != keys.size()) return false;
        for (String key : keys) if (node.path(key).isMissingNode() || node.path(key).isNull()) return false;
        return true;
    }

    private static Response accepted(JdbcEnergyRunRepository.Run run) {
        return Response.status(202).entity(Map.of("runId", run.runId(), "status", "QUEUED",
                "createdAt", run.createdAt().toString())).build();
    }

    private static Response error(int status, String code, String message) {
        return Response.status(status).entity(Map.of("schemaVersion", "error/v1", "status", status,
                "code", code, "message", message)).build();
    }
}
