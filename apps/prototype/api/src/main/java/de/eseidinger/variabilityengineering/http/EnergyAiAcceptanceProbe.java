package de.eseidinger.variabilityengineering.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.eseidinger.variabilityengineering.energy.ai.ExplanationModel;
import de.eseidinger.variabilityengineering.persistence.EnergyRetention;
import de.eseidinger.variabilityengineering.persistence.JdbcEnergyRunRepository;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Private, synthetic-only acceptance probe for a disposable scheduled component.
 * It is not a Quarkus endpoint and requires no database, provider or network.
 */
public final class EnergyAiAcceptanceProbe {
    private static final Logger LOG = Logger.getLogger(EnergyAiAcceptanceProbe.class);
    private static final String RUN_ID = "run-example-0001";
    private static final String PRINCIPAL = "prn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] CASES = {"AI08.foreign_source", "AI08.unresolved_pointer",
            "AI08.changed_value", "AI08.foreign_run", "AI11.source_count",
            "AI11.byte_limit", "AI11.missing_version"};

    private EnergyAiAcceptanceProbe() { }

    public static void main(String[] args) {
        if (args.length != 1 || !"--synthetic-only".equals(args[0])) {
            throw new IllegalArgumentException("synthetic-only flag required");
        }
        configureLogging();
        try {
            new EnergyAiAcceptanceProbeRunner().run();
        } catch (IOException | RuntimeException exception) {
            LOG.errorf("energy_ai_acceptance status=FAIL cause=%s",
                    exception.getClass().getSimpleName());
            throw new IllegalStateException("synthetic AI acceptance probe failed");
        }
    }

    private static void configureLogging() {
        var root = java.util.logging.Logger.getLogger("");
        for (var existing : root.getHandlers()) root.removeHandler(existing);
        var handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + System.lineSeparator();
            }
        });
        root.addHandler(handler);
        root.setLevel(Level.INFO);
    }

    private static final class EnergyAiAcceptanceProbeRunner {
        private final String request;
        private final String result;
        private final String candidate;
        private final String validExplanation;
        private final InMemoryRuns runs;
        private final EnergyEvidenceService evidence;
        private final ExplanationService explanations;
        private final EnergyAiExplanationResource route;
        private final EnergyOptimizationResource readRoute;
        private final AtomicInteger modelCalls = new AtomicInteger();
        private final HttpHeaders headers = fixtureHeaders();
        private String modelOutput;

        EnergyAiAcceptanceProbeRunner() throws IOException {
            request = fixture("optimization-request.json");
            result = fixture("optimization-result.json");
            candidate = fixture("candidate.json");
            validExplanation = fixture("ai-explanation.json");
            var run = new JdbcEnergyRunRepository.Run(RUN_ID, PRINCIPAL, request,
                    "energy-reference-wasserburg-am-inn-2024", "1.0.0", "f".repeat(64),
                    "SUCCEEDED", Instant.parse("2026-10-10T10:00:00Z"), null,
                    Instant.parse("2026-10-10T10:00:01Z"), result, null, null);
            runs = new InMemoryRuns(run, candidate);
            evidence = new EnergyEvidenceService();
            evidence.mapper = MAPPER;
            evidence.runs = runs;
            explanations = new ExplanationService();
            explanations.mapper = MAPPER;
            explanations.model = (ExplanationModel) approved -> {
                modelCalls.incrementAndGet();
                return modelOutput;
            };
            route = new EnergyAiExplanationResource();
            route.runs = runs;
            route.retention = new EnergyRetention() {
                @Override
                public void audit(String principal, String runId, String action, boolean allowed) {
                    // Deliberately in memory; no database binding is present.
                }
            };
            route.evidence = evidence;
            route.explanations = explanations;
            route.enabled = true;
            readRoute = new EnergyOptimizationResource();
            readRoute.runs = runs;
            readRoute.retention = route.retention;
            readRoute.mapper = MAPPER;
        }

        void run() throws IOException {
            JsonNode baseline = evidence.prepare(runs.run);
            check(baseline.size() == 4, "baseline evidence");
            modelOutput = validExplanation;
            checkStatus(route.explain(headers, RUN_ID), 200, null);
            int initialCalls = modelCalls.get();
            check(initialCalls == 1, "baseline model invocation");

            for (String mutation : new String[]{"foreign_source", "unresolved_pointer",
                    "changed_value", "foreign_run"}) {
                var changed = (ObjectNode) MAPPER.readTree(validExplanation);
                var citation = (ObjectNode) changed.path("claims").get(0).path("citations").get(0);
                switch (mutation) {
                    case "foreign_source" -> citation.put("source", "unapproved");
                    case "unresolved_pointer" -> citation.put("pointer", "/missing");
                    case "changed_value" -> citation.put("value", "altered");
                    case "foreign_run" -> changed.put("runId", "run-foreign-0001");
                    default -> throw new IllegalStateException("unknown mutation");
                }
                modelOutput = changed.toString();
                checkStatus(route.explain(headers, RUN_ID), 502, "invalid_ai_explanation");
                checkStable();
                passed("AI08." + mutation);
            }
            check(modelCalls.get() == initialCalls + 4, "controlled model invocations");

            var sixteen = MAPPER.createObjectNode();
            for (int index = 0; index < 16; index++) {
                var source = sixteen.putObject("source" + index);
                source.put("category", "CALCULATED_OUTPUT");
                source.putObject("document").put("value", index);
            }
            EnergyEvidenceService.requireBounds(sixteen, MAPPER);
            var seventeen = sixteen.deepCopy();
            seventeen.set("source16", sixteen.path("source0"));
            expectTooLarge(seventeen);
            checkStable();
            passed("AI11.source_count");

            var bytes = (ObjectNode) baseline.deepCopy();
            var warnings = (com.fasterxml.jackson.databind.node.ArrayNode)
                    bytes.path("result").path("document").path("warnings");
            while (serializedBound(bytes) <= 65536) {
                warnings.addObject().put("code", "synthetic_acceptance")
                        .put("message", "x".repeat(450));
            }
            expectTooLarge(bytes);
            String oversizedResult = bytes.path("result").path("document").toString();
            check(oversizedResult.getBytes(StandardCharsets.UTF_8).length <= 65536,
                    "synthetic result exceeds stored result limit");
            runs.current = new JdbcEnergyRunRepository.Run(RUN_ID, PRINCIPAL, request,
                    runs.run.datasetId(), runs.run.version(), runs.run.manifestSha256(),
                    "SUCCEEDED", runs.run.createdAt(), null, runs.run.completedAt(),
                    oversizedResult, null, null);
            int beforeOversized = modelCalls.get();
            checkStatus(route.explain(headers, RUN_ID), 422, "evidence_unavailable");
            check(modelCalls.get() == beforeOversized, "no model call for oversized evidence");
            runs.current = runs.run;
            checkStable();
            passed("AI11.byte_limit");

            var incomplete = (ObjectNode) MAPPER.readTree(result);
            incomplete.remove("modelVersion");
            var missingRun = new JdbcEnergyRunRepository.Run(RUN_ID, PRINCIPAL, request,
                    runs.run.datasetId(), runs.run.version(), runs.run.manifestSha256(),
                    "SUCCEEDED", runs.run.createdAt(), null, runs.run.completedAt(),
                    incomplete.toString(), null, null);
            runs.current = missingRun;
            int before = modelCalls.get();
            checkStatus(route.explain(headers, RUN_ID), 422, "evidence_unavailable");
            check(modelCalls.get() == before, "no model call for incomplete evidence");
            runs.current = runs.run;
            checkStable();
            passed("AI11.missing_version");
            check(modelCalls.get() == initialCalls + 4, "no model call for evidence limits");
            LOG.infof("energy_ai_acceptance status=PASS cases=%d", CASES.length);
        }

        private void checkStable() {
            check(runs.current == runs.run, "stored run unchanged");
            check(result.equals(runs.run.resultJson()), "result unchanged");
            check(candidate.equals(runs.candidate), "candidate unchanged");
            check(runs.findOwned(RUN_ID, PRINCIPAL).isPresent(), "run remains readable");
            Response response = readRoute.run(headers, RUN_ID);
            checkStatus(response, 200, null);
            check(MAPPER.valueToTree(response.getEntity()).equals(readResult()),
                    "read route result changed");
        }

        private JsonNode readResult() {
            try {
                return MAPPER.readTree(result);
            } catch (IOException exception) {
                throw new IllegalStateException("packaged result unreadable");
            }
        }

        private static void passed(String name) {
            LOG.infof("energy_ai_acceptance case=%s status=PASS", name);
        }
    }

    private static String fixture(String name) throws IOException {
        try (var stream = EnergyAiAcceptanceProbe.class.getResourceAsStream(
                "/energy-acceptance/" + name)) {
            if (stream == null) throw new IOException("missing packaged synthetic fixture");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long serializedBound(JsonNode node) throws IOException {
        return MAPPER.writeValueAsBytes(node).length + separatorSpaces(node);
    }

    private static long separatorSpaces(JsonNode node) {
        if (!node.isContainerNode()) return 0;
        long spaces = Math.max(0, node.size() - 1) + (node.isObject() ? node.size() : 0);
        for (JsonNode child : node) spaces += separatorSpaces(child);
        return spaces;
    }

    private static void expectTooLarge(ObjectNode sources) throws IOException {
        try {
            EnergyEvidenceService.requireBounds(sources, MAPPER);
        } catch (EnergyEvidenceService.EvidenceTooLarge expected) {
            return;
        }
        throw new IllegalStateException("oversized evidence was accepted");
    }

    private static void checkStatus(Response response, int status, String code) {
        check(response.getStatus() == status, "unexpected response status");
        if (code != null) {
            check(response.getEntity() instanceof Map<?, ?>, "missing error envelope");
            check(code.equals(((Map<?, ?>) response.getEntity()).get("code")), "wrong error code");
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new IllegalStateException(label);
    }

    private static HttpHeaders fixtureHeaders() {
        Map<String, String> values = Map.of(
                "X-Platform-Principal-Id", PRINCIPAL,
                "X-Platform-Principal-Kind", "user",
                "X-Platform-Principal-Issuer", "https://identity.example.test",
                "X-Platform-Principal-Subject", "fixture-user",
                "X-Platform-Principal-Group-Ids", "grp_11111111111111111111111111111111");
        return (HttpHeaders) Proxy.newProxyInstance(HttpHeaders.class.getClassLoader(),
                new Class<?>[]{HttpHeaders.class}, (proxy, method, args) -> {
                    if ("getHeaderString".equals(method.getName())) return values.get(args[0]);
                    throw new UnsupportedOperationException("unsupported fixture header operation");
                });
    }

    private static final class InMemoryRuns extends JdbcEnergyRunRepository {
        private final Run run;
        private final String candidate;
        private Run current;

        InMemoryRuns(Run run, String candidate) {
            this.run = run;
            this.candidate = candidate;
            this.current = run;
        }

        @Override
        public Optional<Run> findOwned(String runId, String principalId) {
            return RUN_ID.equals(runId) && PRINCIPAL.equals(principalId)
                    ? Optional.of(current) : Optional.empty();
        }

        @Override
        public Optional<String> recommendedCandidate(String runId, String configurationId) {
            return RUN_ID.equals(runId) && "cfg-pv10-inv1-bat1".equals(configurationId)
                    ? Optional.of(candidate) : Optional.empty();
        }
    }
}
