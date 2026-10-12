package de.eseidinger.variabilityengineering.http;

import de.eseidinger.variabilityengineering.adapter.foodservice.FoodServiceFixtureAdapter;
import de.eseidinger.variabilityengineering.core.AnalysisEngine;
import de.eseidinger.variabilityengineering.core.AnalysisPopulation;
import de.eseidinger.variabilityengineering.core.AnalysisQuery;
import de.eseidinger.variabilityengineering.core.AnalysisResult;
import de.eseidinger.variabilityengineering.core.DimensionDefinition;
import de.eseidinger.variabilityengineering.core.DimensionValue;
import de.eseidinger.variabilityengineering.core.ElementUnavailabilityScenario;
import de.eseidinger.variabilityengineering.core.ImpactResult;
import de.eseidinger.variabilityengineering.core.Variant;
import de.eseidinger.variabilityengineering.core.VariantRecord;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** HTTP DTOs and resource for the read-only food-service analysis MVP. */
@Path("/api/populations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FoodServiceAnalysisResource {

    private final AnalysisEngine engine = new AnalysisEngine();

    @Inject
    FoodServicePopulationStore store;

    @Context
    HttpHeaders headers;

    @GET
    @Path("/{populationId}")
    public PopulationSummaryResponse summary(@PathParam("populationId") String populationId) {
        authorize("GET");
        var population = store.population(populationId);
        var result = engine.analyze(population, AnalysisQuery.unfiltered());
        return PopulationSummaryResponse.from(population, store.rejectedRecords(populationId), result);
    }

    @GET
    @Path("/{populationId}/records/{recordId}")
    public RecordDetailResponse record(@PathParam("populationId") String populationId, @PathParam("recordId") String recordId) {
        authorize("GET");
        var population = store.population(populationId);
        var record = population.records().stream()
                .filter(candidate -> candidate.id().equals(recordId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("unknown record: " + recordId));
        return new RecordDetailResponse(PopulationReference.from(population), RecordResponse.from(record));
    }

    @POST
    @Path("/{populationId}/analysis")
    public AnalysisResponse analyze(@PathParam("populationId") String populationId, AnalysisRequest request) {
        authorize("POST");
        var population = store.population(populationId);
        var query = query(request);
        try {
            return AnalysisResponse.from(engine.analyze(population, query), PopulationReference.from(population));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage(), exception);
        }
    }

    @POST
    @Path("/{populationId}/elements/{elementId}/leverage")
    public LeverageResponse leverage(
            @PathParam("populationId") String populationId,
            @PathParam("elementId") String elementId,
            LeverageRequest request) {
        authorize("POST");
        var population = store.population(populationId);
        var effectiveRequest = request == null ? new LeverageRequest(null, Set.of()) : request;
        try {
            var query = query(effectiveRequest.query());
            var dimensionIds = effectiveRequest.dimensionIds() == null || effectiveRequest.dimensionIds().isEmpty()
                    ? Set.of("cuisine", "diet")
                    : Set.copyOf(effectiveRequest.dimensionIds());
            var leverage = engine.leverage(population, query, elementId, dimensionIds);
            return new LeverageResponse(
                    PopulationReference.from(population),
                    QueryResponse.from(query),
                    leverage.elementId(),
                    leverage.recordCount(),
                    new TreeMap<>(leverage.knownDimensionValues()));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage(), exception);
        }
    }

    @POST
    @Path("/{populationId}/element-unavailability")
    public ImpactResponse elementUnavailability(@PathParam("populationId") String populationId, ScenarioRequest request) {
        authorize("POST");
        var population = store.population(populationId);
        if (request == null || request.unavailableElementIds() == null || request.unavailableElementIds().isEmpty()) {
            throw new BadRequestException("unavailableElementIds must not be empty");
        }
        try {
            var query = query(request.query());
            var scenario = new ElementUnavailabilityScenario(request.unavailableElementIds());
            return ImpactResponse.from(engine.compareElementUnavailability(population, query, scenario), PopulationReference.from(population));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(exception.getMessage(), exception);
        }
    }

    private static AnalysisQuery query(AnalysisRequest request) {
        if (request == null) {
            return AnalysisQuery.unfiltered();
        }
        try {
            var filters = new TreeMap<String, AnalysisQuery.DimensionFilter>();
            if (request.dimensionFilters() != null) {
                for (var entry : request.dimensionFilters().entrySet()) {
                    filters.put(entry.getKey(), entry.getValue().toCore());
                }
            }
            return new AnalysisQuery(
                    filters,
                    request.requiredElementIds() == null ? Set.of() : request.requiredElementIds(),
                    request.groupingDimensions() == null ? List.of() : request.groupingDimensions());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadRequestException(exception.getMessage(), exception);
        }
    }

    private void authorize(String method) {
        PopulationAuthorizationFilter.requireAuthorized(headers, method);
    }

    public record AnalysisRequest(
            Map<String, DimensionFilterRequest> dimensionFilters,
            Set<String> requiredElementIds,
            List<String> groupingDimensions) {
    }

    public record DimensionFilterRequest(String requiredState, Set<String> includedValues, Set<String> excludedValues) {
        AnalysisQuery.DimensionFilter toCore() {
            var state = requiredState == null ? null : DimensionValue.State.valueOf(requiredState);
            return new AnalysisQuery.DimensionFilter(
                    state,
                    includedValues == null ? Set.of() : includedValues,
                    excludedValues == null ? Set.of() : excludedValues);
        }
    }

    public record LeverageRequest(AnalysisRequest query, Set<String> dimensionIds) {
    }

    public record ScenarioRequest(AnalysisRequest query, Set<String> unavailableElementIds) {
    }

    public record PopulationReference(String id, String version, Map<String, ArtifactResponse> provenance) {
        static PopulationReference from(AnalysisPopulation population) {
            var provenance = new TreeMap<String, ArtifactResponse>();
            population.provenance().forEach((role, artifact) -> provenance.put(role, ArtifactResponse.from(artifact)));
            return new PopulationReference(population.id(), population.version(), provenance);
        }
    }

    public record ArtifactResponse(String id, String version, String sha256) {
        static ArtifactResponse from(AnalysisPopulation.ArtifactVersion artifact) {
            return new ArtifactResponse(artifact.id(), artifact.version(), artifact.sha256());
        }
    }

    public record DimensionDefinitionResponse(String cardinality, String origin, Set<String> observedValues) {
        static DimensionDefinitionResponse from(DimensionDefinition definition, AnalysisPopulation population) {
            var observedValues = population.records().stream()
                    .map(record -> record.dimensions().get(definition.id()))
                    .filter(value -> value.state() == DimensionValue.State.KNOWN)
                    .flatMap(value -> value.values().stream())
                    .collect(Collectors.toCollection(TreeSet::new));
            return new DimensionDefinitionResponse(
                    definition.cardinality().name(), definition.origin().name(), Set.copyOf(observedValues));
        }
    }

    public record DimensionValueResponse(String state, Set<String> values) {
        static DimensionValueResponse from(DimensionValue value) {
            return new DimensionValueResponse(value.state().name(), value.values());
        }
    }

    public record RecordResponse(String id, Set<String> elementIds, Map<String, DimensionValueResponse> dimensions) {
        static RecordResponse from(VariantRecord record) {
            var dimensions = new TreeMap<String, DimensionValueResponse>();
            record.dimensions().forEach((id, value) -> dimensions.put(id, DimensionValueResponse.from(value)));
            return new RecordResponse(record.id(), record.variant().elementIds(), dimensions);
        }
    }

    public record RejectedRecordResponse(String sourceRecordId, String reason, String detail) {
        static RejectedRecordResponse from(FoodServiceFixtureAdapter.RejectedRecord record) {
            return new RejectedRecordResponse(record.sourceRecordId(), record.reason(), record.detail());
        }
    }

    public record MetricsResponse(
            long recordCount,
            long variantCount,
            long uniqueElementCount,
            Map<String, Long> elementRecordCounts,
            Map<String, Double> elementRecordFrequencies) {
        static MetricsResponse from(AnalysisResult.Metrics metrics) {
            return new MetricsResponse(
                    metrics.recordCount(), metrics.variantCount(), metrics.uniqueElementCount(),
                    new TreeMap<>(metrics.elementRecordCounts()), new TreeMap<>(metrics.elementRecordFrequencies()));
        }
    }

    public record GroupKeyResponse(String dimensionId, String valueKind, String value) {
        static GroupKeyResponse from(AnalysisResult.GroupKey key) {
            return new GroupKeyResponse(key.dimensionId(), key.value().kind().name(), key.value().value());
        }
    }

    public record GroupResponse(List<GroupKeyResponse> path, MetricsResponse metrics, List<GroupResponse> children) {
        static GroupResponse from(AnalysisResult.Group group) {
            return new GroupResponse(
                    group.path().keys().stream().map(GroupKeyResponse::from).toList(),
                    MetricsResponse.from(group.metrics()),
                    group.children().stream().map(GroupResponse::from).toList());
        }
    }

    public record QueryResponse(
            Map<String, DimensionFilterRequest> dimensionFilters,
            Set<String> requiredElementIds,
            List<String> groupingDimensions) {
        static QueryResponse from(AnalysisQuery query) {
            var filters = new TreeMap<String, DimensionFilterRequest>();
            query.dimensionFilters().forEach((dimensionId, filter) -> filters.put(dimensionId,
                    new DimensionFilterRequest(
                            filter.requiredState() == null ? null : filter.requiredState().name(),
                            filter.includedValues(), filter.excludedValues())));
            return new QueryResponse(filters, query.requiredElementIds(), query.groupingDimensions());
        }
    }

    public record AnalysisResponse(
            PopulationReference population,
            QueryResponse query,
            Set<String> selectedRecordIds,
            MetricsResponse metrics,
            List<GroupResponse> groups) {
        static AnalysisResponse from(AnalysisResult result, PopulationReference population) {
            return new AnalysisResponse(
                    population,
                    QueryResponse.from(result.query()),
                    result.selectedRecordIds(),
                    MetricsResponse.from(result.metrics()),
                    result.groups().stream().map(GroupResponse::from).toList());
        }
    }

    public record PopulationSummaryResponse(
            PopulationReference population,
            Map<String, DimensionDefinitionResponse> dimensions,
            MetricsResponse metrics,
            List<RejectedRecordResponse> rejectedRecords) {
        static PopulationSummaryResponse from(
                AnalysisPopulation population,
                List<FoodServiceFixtureAdapter.RejectedRecord> rejectedRecords,
                AnalysisResult result) {
            var dimensions = new TreeMap<String, DimensionDefinitionResponse>();
            population.dimensionDefinitions().forEach((id, definition) ->
                    dimensions.put(id, DimensionDefinitionResponse.from(definition, population)));
            return new PopulationSummaryResponse(
                    PopulationReference.from(population), dimensions, MetricsResponse.from(result.metrics()),
                    rejectedRecords.stream().map(RejectedRecordResponse::from).toList());
        }
    }

    public record RecordDetailResponse(PopulationReference population, RecordResponse record) {
    }

    public record LeverageResponse(
            PopulationReference population,
            QueryResponse query,
            String elementId,
            long recordCount,
            Map<String, Set<String>> knownDimensionValues) {
    }

    public record ScenarioResponse(Set<String> unavailableElementIds) {
        static ScenarioResponse from(ElementUnavailabilityScenario scenario) {
            return new ScenarioResponse(scenario.unavailableElementIds());
        }
    }

    public record MetricsDeltaResponse(long recordCount, long variantCount, long uniqueElementCount) {
        static MetricsDeltaResponse from(ImpactResult.MetricsDelta delta) {
            return new MetricsDeltaResponse(delta.recordCount(), delta.variantCount(), delta.uniqueElementCount());
        }
    }

    public record RelativeMetricsDeltaResponse(Double recordCount, Double variantCount, Double uniqueElementCount) {
        static RelativeMetricsDeltaResponse from(ImpactResult.RelativeMetricsDelta delta) {
            return new RelativeMetricsDeltaResponse(
                    delta.recordCount(), delta.variantCount(), delta.uniqueElementCount());
        }
    }

    public record GroupDeltaResponse(List<GroupKeyResponse> path, MetricsDeltaResponse delta) {
        static GroupDeltaResponse from(Map.Entry<AnalysisResult.GroupPath, ImpactResult.MetricsDelta> entry) {
            return new GroupDeltaResponse(
                    entry.getKey().keys().stream().map(GroupKeyResponse::from).toList(),
                    MetricsDeltaResponse.from(entry.getValue()));
        }
    }

    public record ImpactResponse(
            PopulationReference population,
            QueryResponse query,
            ScenarioResponse scenario,
            AnalysisResponse baseline,
            AnalysisResponse scenarioResult,
            Set<String> lostRecordIds,
            Set<Set<String>> lostVariants,
            Set<String> lostElementIds,
            MetricsDeltaResponse rootDelta,
            RelativeMetricsDeltaResponse relativeDelta,
            List<GroupDeltaResponse> groupDeltas) {
        static ImpactResponse from(ImpactResult impact, PopulationReference population) {
            var lostVariants = impact.lostVariants().stream()
                    .map(Variant::elementIds)
                    .collect(Collectors.toUnmodifiableSet());
            var groupDeltas = impact.groupDeltas().entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                    .map(GroupDeltaResponse::from)
                    .toList();
            return new ImpactResponse(
                    population,
                    QueryResponse.from(impact.baseline().query()),
                    ScenarioResponse.from(impact.scenario()),
                    AnalysisResponse.from(impact.baseline(), population),
                    AnalysisResponse.from(impact.scenarioResult(), population),
                    impact.lostRecordIds(), lostVariants, impact.lostElementIds(),
                    MetricsDeltaResponse.from(impact.rootDelta()),
                    RelativeMetricsDeltaResponse.from(impact.relativeDelta()), groupDeltas);
        }
    }
}
