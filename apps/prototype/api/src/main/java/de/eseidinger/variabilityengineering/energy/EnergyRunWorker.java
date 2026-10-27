package de.eseidinger.variabilityengineering.energy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.eseidinger.variabilityengineering.adapter.odip.VerifiedEnergyPublication;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyPublicationRepository;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyRunRepository;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import java.util.concurrent.Future;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** One bounded worker for the declared single-replica service. Claims and terminal writes are transactional. */
@ApplicationScoped
public class EnergyRunWorker {
    private static final Logger LOG = Logger.getLogger(EnergyRunWorker.class);
    @Inject JdbcEnergyRunRepository runs;
    @Inject JdbcEnergyPublicationRepository publications;
    @Inject ObjectMapper mapper;

    private final java.util.concurrent.ExecutorService computation = Executors.newSingleThreadExecutor();

    void recover(@Observes StartupEvent event) {
        runs.requeueInterrupted();
    }

    @Scheduled(every = "2s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void processNext() {
        var next = runs.claimNext();
        if (next.isEmpty()) return;
        var claim = next.get();
        var task = computation.submit(() -> calculate(claim.run()));
        await(claim, task);
    }

    void await(JdbcEnergyRunRepository.Claimed claim, Future<Output> task) {
        try {
            var output = task.get(300, TimeUnit.SECONDS);
            runs.complete(claim, output.status(), output.resultJson(), null, output.candidates());
        } catch (TimeoutException exception) {
            task.cancel(true);
            fail(claim, "run_timeout", "The 300-second run deadline was exceeded.");
        } catch (InterruptedException exception) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            fail(claim, "run_interrupted", "The run was interrupted.");
        } catch (java.util.concurrent.ExecutionException exception) {
            fail(claim, "run_failed", "The energy run could not be completed.");
        }
    }

    private void fail(JdbcEnergyRunRepository.Claimed claim, String code, String message) {
        LOG.warnf("event=energy_run_failed run_id=%s code=%s", claim.run().runId(), code);
        var error = mapper.createObjectNode();
        error.put("schemaVersion", "error/v1");
        error.put("status", 500);
        error.put("code", code);
        error.put("message", message);
        runs.complete(claim, "FAILED", null, error.toString(), List.of());
    }

    private Output calculate(JdbcEnergyRunRepository.Run run) throws Exception {
        var cached = publications.find(run.datasetId(), run.version())
                .orElseThrow(() -> new IllegalStateException("verified publication disappeared"));
        VerifiedEnergyPublication publication = cached.publication();
        if (!run.manifestSha256().equals(publication.manifestSha256())) {
            throw new IllegalStateException("verified publication changed after acceptance");
        }
        var request = mapper.readValue(run.requestJson(), EnergyOptimizationRequest.class);
        var optimization = new EnergyOptimizer().optimize(request, publication.catalog(), publication.profile());
        var dataset = mapper.createObjectNode();
        dataset.put("datasetId", run.datasetId());
        dataset.put("version", run.version());
        dataset.put("schemaRef", EnergyOptimizationRequest.PROFILE);
        dataset.put("manifestSha256", publication.manifestSha256());

        var result = mapper.createObjectNode();
        result.put("schemaVersion", "energy-optimization-result/v1");
        result.put("runId", run.runId());
        result.put("status", optimization.status());
        result.set("dataset", dataset);
        result.put("modelVersion", EnergyFeatureModel.VERSION);
        result.put("simulationVersion", EnergySimulation.VERSION);
        result.put("optimizerVersion", EnergyOptimizer.VERSION);
        result.put("optimalityStatus", optimization.optimalityStatus());
        result.put("evaluatedCandidateCount", optimization.candidates().size());
        result.put("feasibleCandidateCount", optimization.feasibleCandidateCount());
        if (optimization.recommendedConfigurationId() == null) result.putNull("recommendedConfigurationId");
        else result.put("recommendedConfigurationId", optimization.recommendedConfigurationId());
        result.set("warnings", mapper.valueToTree(publication.warnings()));
        result.put("createdAt", run.createdAt().toString());
        result.put("completedAt", Instant.now().toString());

        var byId = new HashMap<String, EnergyOptimizer.Candidate>();
        optimization.candidates().forEach(candidate -> byId.put(candidate.configuration().id(), candidate));
        var ordered = new ArrayList<EnergyOptimizer.Candidate>();
        optimization.ranking().forEach(id -> ordered.add(byId.remove(id)));
        ordered.addAll(byId.values().stream().sorted(Comparator.comparing(c -> c.configuration().id())).toList());
        var serialized = ordered.stream().map(this::candidateJson).toList();
        return new Output(optimization.status(), result.toString(), serialized);
    }

    private String candidateJson(EnergyOptimizer.Candidate candidate) {
        var response = mapper.createObjectNode();
        response.put("schemaVersion", "energy-candidate/v1");
        response.put("configurationId", candidate.configuration().id());
        var components = mapper.createArrayNode();
        var configuration = candidate.configuration();
        components.add(component(configuration.pvModule().id(), "PV_MODULE", configuration.pvQuantity()));
        components.add(component(configuration.inverter().id(), "INVERTER", 1));
        if (configuration.battery() != null) {
            components.add(component(configuration.battery().id(), "BATTERY", configuration.batteryQuantity()));
        }
        response.set("components", components);
        response.put("feasible", candidate.feasible());
        var constraints = mapper.createArrayNode();
        for (var item : candidate.constraints()) {
            ObjectNode node = mapper.valueToTree(item);
            if (item.unit() == null) node.remove("unit");
            constraints.add(node);
        }
        response.set("constraints", constraints);
        response.set("metrics", mapper.valueToTree(candidate.metrics()));
        return response.toString();
    }

    private JsonNode component(String id, String type, int quantity) {
        var component = mapper.createObjectNode();
        component.put("componentId", id);
        component.put("type", type);
        component.put("quantity", quantity);
        component.put("existing", false);
        return component;
    }

    @PreDestroy
    void stop() {
        computation.shutdownNow();
    }

    record Output(String status, String resultJson, List<String> candidates) { }
}
