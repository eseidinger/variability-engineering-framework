package de.eseidinger.variabilityengineering.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Stateless calculations over a versioned, domain-independent analysis population. */
public final class AnalysisEngine {

    private static final Comparator<AnalysisResult.GroupValue> GROUP_VALUE_ORDER = Comparator
            .comparing(AnalysisResult.GroupValue::kind)
            .thenComparing(value -> value.value() == null ? "" : value.value());

    public AnalysisResult analyze(AnalysisPopulation population, AnalysisQuery query) {
        Objects.requireNonNull(population, "population must not be null");
        Objects.requireNonNull(query, "query must not be null");
        validateQuery(population, query);
        var selected = population.records().stream().filter(record -> matches(record, query)).toList();
        return new AnalysisResult(
                population.id(),
                population.version(),
                query,
                selected.stream().map(VariantRecord::id).collect(Collectors.toUnmodifiableSet()),
                metrics(selected),
                buildGroups(selected, query.groupingDimensions(), 0, List.of()));
    }

    public ElementLeverage leverage(
            AnalysisPopulation population,
            AnalysisQuery query,
            String elementId,
            Set<String> dimensionIds) {
        Objects.requireNonNull(population, "population must not be null");
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(elementId, "elementId must not be null");
        Objects.requireNonNull(dimensionIds, "dimensionIds must not be null");
        validateQuery(population, query);
        if (!population.elementIds().contains(elementId)) {
            throw new IllegalArgumentException("leverage references unknown element: " + elementId);
        }
        validateDimensionIds(population, dimensionIds);
        var selected = population.records().stream()
                .filter(record -> matches(record, query))
                .filter(record -> record.variant().elementIds().contains(elementId))
                .toList();
        var values = new TreeMap<String, Set<String>>();
        for (var dimensionId : dimensionIds) {
            values.put(dimensionId, selected.stream()
                    .map(record -> record.dimensions().get(dimensionId))
                    .filter(value -> value.state() == DimensionValue.State.KNOWN)
                    .flatMap(value -> value.values().stream())
                    .collect(Collectors.toUnmodifiableSet()));
        }
        return new ElementLeverage(elementId, selected.size(), values);
    }

    public ImpactResult compareElementUnavailability(
            AnalysisPopulation population,
            AnalysisQuery query,
            ElementUnavailabilityScenario scenario) {
        Objects.requireNonNull(scenario, "scenario must not be null");
        var unknownElements = new TreeSet<>(scenario.unavailableElementIds());
        unknownElements.removeAll(population.elementIds());
        if (!unknownElements.isEmpty()) {
            throw new IllegalArgumentException("scenario references unknown elements: " + unknownElements);
        }
        var baseline = analyze(population, query);
        var baselineRecords = population.records().stream().filter(record -> matches(record, query)).toList();
        var scenarioRecords = baselineRecords.stream()
                .filter(record -> java.util.Collections.disjoint(record.variant().elementIds(), scenario.unavailableElementIds()))
                .toList();
        var scenarioResult = new AnalysisResult(
                population.id(),
                population.version(),
                query,
                scenarioRecords.stream().map(VariantRecord::id).collect(Collectors.toUnmodifiableSet()),
                metrics(scenarioRecords),
                buildGroups(scenarioRecords, query.groupingDimensions(), 0, List.of()));
        var lostRecords = baselineRecords.stream()
                .filter(record -> !scenarioRecords.contains(record))
                .map(VariantRecord::id)
                .collect(Collectors.toUnmodifiableSet());
        var remainingVariants = scenarioRecords.stream().map(VariantRecord::variant).collect(Collectors.toSet());
        var lostVariants = baselineRecords.stream().map(VariantRecord::variant)
                .filter(variant -> !remainingVariants.contains(variant))
                .collect(Collectors.toUnmodifiableSet());
        var remainingElements = scenarioRecords.stream()
                .flatMap(record -> record.variant().elementIds().stream())
                .collect(Collectors.toSet());
        var lostElements = baselineRecords.stream()
                .flatMap(record -> record.variant().elementIds().stream())
                .filter(elementId -> !remainingElements.contains(elementId))
                .collect(Collectors.toUnmodifiableSet());
        return new ImpactResult(
                scenario,
                baseline,
                scenarioResult,
                lostRecords,
                lostVariants,
                lostElements,
                ImpactResult.MetricsDelta.between(baseline.metrics(), scenarioResult.metrics()),
                ImpactResult.RelativeMetricsDelta.between(baseline.metrics(), scenarioResult.metrics()),
                groupDeltas(baseline, scenarioResult));
    }

    private static void validateQuery(AnalysisPopulation population, AnalysisQuery query) {
        validateDimensionIds(population, query.dimensionFilters().keySet());
        validateDimensionIds(population, Set.copyOf(query.groupingDimensions()));
        var unknownElements = new TreeSet<>(query.requiredElementIds());
        unknownElements.removeAll(population.elementIds());
        if (!unknownElements.isEmpty()) {
            throw new IllegalArgumentException("query references unknown elements: " + unknownElements);
        }
    }

    private static void validateDimensionIds(AnalysisPopulation population, Set<String> dimensionIds) {
        var unknown = new TreeSet<>(dimensionIds);
        unknown.removeAll(population.dimensionDefinitions().keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("query references unknown dimensions: " + unknown);
        }
    }

    private static boolean matches(VariantRecord record, AnalysisQuery query) {
        if (!record.variant().elementIds().containsAll(query.requiredElementIds())) {
            return false;
        }
        for (var entry : query.dimensionFilters().entrySet()) {
            var value = record.dimensions().get(entry.getKey());
            var filter = entry.getValue();
            if (filter.requiredState() != null && value.state() != filter.requiredState()) {
                return false;
            }
            if (!filter.includedValues().isEmpty()
                    && (value.state() != DimensionValue.State.KNOWN
                    || java.util.Collections.disjoint(value.values(), filter.includedValues()))) {
                return false;
            }
            if (!filter.excludedValues().isEmpty()
                    && (value.state() != DimensionValue.State.KNOWN
                    || !java.util.Collections.disjoint(value.values(), filter.excludedValues()))) {
                return false;
            }
        }
        return true;
    }

    private static AnalysisResult.Metrics metrics(List<VariantRecord> records) {
        var counts = new TreeMap<String, Long>();
        var variants = new LinkedHashSet<Variant>();
        var elements = new LinkedHashSet<String>();
        for (var record : records) {
            variants.add(record.variant());
            for (var elementId : record.variant().elementIds()) {
                elements.add(elementId);
                counts.merge(elementId, 1L, Long::sum);
            }
        }
        var frequencies = new TreeMap<String, Double>();
        if (!records.isEmpty()) {
            counts.forEach((elementId, count) -> frequencies.put(elementId, count.doubleValue() / records.size()));
        }
        return new AnalysisResult.Metrics(records.size(), variants.size(), elements.size(), counts, frequencies);
    }

    private static List<AnalysisResult.Group> buildGroups(
            List<VariantRecord> records,
            List<String> dimensions,
            int index,
            List<AnalysisResult.GroupKey> path) {
        if (index == dimensions.size()) {
            return List.of();
        }
        var dimensionId = dimensions.get(index);
        var partitions = new TreeMap<AnalysisResult.GroupValue, List<VariantRecord>>(GROUP_VALUE_ORDER);
        for (var record : records) {
            for (var groupValue : groupValues(record.dimensions().get(dimensionId))) {
                partitions.computeIfAbsent(groupValue, ignored -> new ArrayList<>()).add(record);
            }
        }
        var groups = new ArrayList<AnalysisResult.Group>();
        for (var entry : partitions.entrySet()) {
            var nextPath = new ArrayList<>(path);
            nextPath.add(new AnalysisResult.GroupKey(dimensionId, entry.getKey()));
            groups.add(new AnalysisResult.Group(
                    new AnalysisResult.GroupPath(nextPath),
                    metrics(entry.getValue()),
                    buildGroups(entry.getValue(), dimensions, index + 1, nextPath)));
        }
        return List.copyOf(groups);
    }

    private static Set<AnalysisResult.GroupValue> groupValues(DimensionValue value) {
        if (value.state() == DimensionValue.State.UNKNOWN) {
            return Set.of(AnalysisResult.GroupValue.unknown());
        }
        if (value.values().isEmpty()) {
            return Set.of(AnalysisResult.GroupValue.knownEmpty());
        }
        return value.values().stream().map(AnalysisResult.GroupValue::value).collect(Collectors.toUnmodifiableSet());
    }

    private static Map<AnalysisResult.GroupPath, ImpactResult.MetricsDelta> groupDeltas(
            AnalysisResult baseline,
            AnalysisResult scenario) {
        var baselineMetrics = flattenGroups(baseline.groups());
        var scenarioMetrics = flattenGroups(scenario.groups());
        var paths = new TreeSet<>(Comparator.comparing(AnalysisResult.GroupPath::toString));
        paths.addAll(baselineMetrics.keySet());
        paths.addAll(scenarioMetrics.keySet());
        var deltas = new LinkedHashMap<AnalysisResult.GroupPath, ImpactResult.MetricsDelta>();
        for (var path : paths) {
            var before = baselineMetrics.getOrDefault(path, zeroMetrics());
            var after = scenarioMetrics.getOrDefault(path, zeroMetrics());
            deltas.put(path, ImpactResult.MetricsDelta.between(before, after));
        }
        return Map.copyOf(deltas);
    }

    private static Map<AnalysisResult.GroupPath, AnalysisResult.Metrics> flattenGroups(List<AnalysisResult.Group> groups) {
        var result = new HashMap<AnalysisResult.GroupPath, AnalysisResult.Metrics>();
        for (var group : groups) {
            result.put(group.path(), group.metrics());
            result.putAll(flattenGroups(group.children()));
        }
        return result;
    }

    private static AnalysisResult.Metrics zeroMetrics() {
        return new AnalysisResult.Metrics(0, 0, 0, Map.of(), Map.of());
    }
}
