package de.eseidinger.variabilityengineering.adapter.foodservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.eseidinger.variabilityengineering.core.AnalysisEngine;
import de.eseidinger.variabilityengineering.core.AnalysisPopulation;
import de.eseidinger.variabilityengineering.core.AnalysisQuery;
import de.eseidinger.variabilityengineering.core.AnalysisResult;
import de.eseidinger.variabilityengineering.core.DimensionDefinition;
import de.eseidinger.variabilityengineering.core.DimensionValue;
import de.eseidinger.variabilityengineering.core.ElementUnavailabilityScenario;
import de.eseidinger.variabilityengineering.core.ImpactResult;
import de.eseidinger.variabilityengineering.core.Variant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodServiceFixtureConformanceTest {

    private static final Set<String> SPECIAL_GROUP_VALUES = Set.of("KNOWN_EMPTY", "UNKNOWN");

    private final FoodServiceFixtureAdapter adapter = new FoodServiceFixtureAdapter();
    private final AnalysisEngine engine = new AnalysisEngine();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reproducesTheVersionedFixtureOracle() throws IOException {
        var expected = expectedResults();
        var imported = adapter.importFixture(fixtureDirectory());
        var population = imported.population();

        assertEquals(text(expected, "fixtureId"), population.id());
        assertEquals(text(expected, "version"), population.version());
        assertEquals(expected.path("import").path("sourceRecordCount").asInt(),
                population.records().size() + imported.rejectedRecords().size());
        assertEquals(expected.path("import").path("acceptedRecordCount").asInt(), population.records().size());
        assertEquals(expectedRejectedRecords(expected.path("import").path("rejectedRecords")), imported.rejectedRecords());
        assertProvenance(expected.path("populationProvenance"), population.provenance());
        assertDimensionDefinitions(expected.path("dimensionDefinitions"), population.dimensionDefinitions());
        assertRecords(expected.path("records"), population);
        assertEqualStructuralVariants(expected.path("equalStructuralVariants"), population);

        var baseline = engine.analyze(population, AnalysisQuery.unfiltered());
        assertMetrics(expected.path("rootMetrics"), baseline.metrics());
        assertEquals(longMap(expected.path("elementRecordCounts")), baseline.metrics().elementRecordCounts());
        assertGroupRecordCounts(expected.path("groupRecordCounts"), population);
        assertQueries(expected.path("queries"), expected.path("groupRecordCounts").path("allergen"), population);
        assertGarlicLeverage(expected.path("elementLeverage").path("garlic"), population);
        assertGarlicScenario(expected.path("scenarios").path("garlicUnavailable"), population);
    }

    @Test
    void rejectsChangesToManifestTrackedInputArtifacts(@TempDir Path temporaryDirectory) throws IOException {
        for (var relativePath : List.of(
                "source/recipes.json",
                "mapping/ingredient-aliases.json",
                "derivation/ingredient-classifications.json",
                "adapter/contract.json")) {
            var copiedFixture = temporaryDirectory.resolve(relativePath.replace('/', '-'));
            copyDirectory(fixtureDirectory(), copiedFixture);
            Files.writeString(copiedFixture.resolve(relativePath), "\n", StandardOpenOption.APPEND);

            var exception = assertThrows(IllegalArgumentException.class, () -> adapter.importFixture(copiedFixture));

            assertTrue(exception.getMessage().contains("artifact checksum mismatch for " + relativePath));
        }
    }

    private void assertProvenance(JsonNode expected, Map<String, AnalysisPopulation.ArtifactVersion> actual) {
        assertArtifact(expected.path("sourceDataset"), actual.get("sourceDataset"));
        assertArtifact(expected.path("adapter"), actual.get("adapterContract"));
        assertArtifact(expected.path("mapping"), actual.get("mapping"));
        assertArtifact(expected.path("derivation"), actual.get("derivation"));
    }

    private static void assertArtifact(JsonNode expected, AnalysisPopulation.ArtifactVersion actual) {
        assertEquals(text(expected, "id"), actual.id());
        assertEquals(text(expected, "version"), actual.version());
        assertEquals(text(expected, "sha256"), actual.sha256());
    }

    private static void assertDimensionDefinitions(JsonNode expected, Map<String, DimensionDefinition> actual) {
        assertEquals(stringsFromFieldNames(expected), actual.keySet());
        expected.properties().forEach(entry -> {
            var definition = actual.get(entry.getKey());
            assertEquals(DimensionDefinition.Cardinality.valueOf(text(entry.getValue(), "cardinality")), definition.cardinality());
            assertEquals(DimensionDefinition.Origin.valueOf(text(entry.getValue(), "origin")), definition.origin());
        });
    }

    private static void assertRecords(JsonNode expected, AnalysisPopulation population) {
        var actualById = new HashMap<String, de.eseidinger.variabilityengineering.core.VariantRecord>();
        population.records().forEach(record -> actualById.put(record.id(), record));
        assertEquals(recordIds(expected), actualById.keySet());
        for (var expectedRecord : expected) {
            var actual = actualById.get(text(expectedRecord, "id"));
            assertEquals(strings(expectedRecord, "elements"), actual.variant().elementIds());
            expectedRecord.path("dimensions").properties().forEach(entry ->
                    assertEquals(expectedDimensionValue(entry.getValue()), actual.dimensions().get(entry.getKey())));
        }
    }

    private static void assertEqualStructuralVariants(JsonNode expected, AnalysisPopulation population) {
        var records = new HashMap<String, Variant>();
        population.records().forEach(record -> records.put(record.id(), record.variant()));
        for (var pair : expected) {
            assertEquals(records.get(pair.get(0).asText()), records.get(pair.get(1).asText()));
        }
    }

    private void assertGroupRecordCounts(JsonNode expected, AnalysisPopulation population) {
        expected.properties().forEach(entry -> {
            var result = engine.analyze(population, new AnalysisQuery(Map.of(), Set.of(), List.of(entry.getKey())));
            var actualCounts = new HashMap<String, Long>();
            result.groups().forEach(group -> actualCounts.put(groupValueKey(group.path().keys().getFirst().value()),
                    group.metrics().recordCount()));
            assertEquals(longMap(entry.getValue()), actualCounts);
        });
    }

    private void assertQueries(JsonNode expected, JsonNode allergens, AnalysisPopulation population) {
        var supportedAllergens = stringsFromFieldNames(allergens);
        supportedAllergens.removeAll(SPECIAL_GROUP_VALUES);
        var queries = Map.of(
                "dietIsVegan", new AnalysisQuery(
                        Map.of("diet", AnalysisQuery.DimensionFilter.including(Set.of("VEGAN"))), Set.of(), List.of()),
                "cuisineIsItalian", new AnalysisQuery(
                        Map.of("cuisine", AnalysisQuery.DimensionFilter.including(Set.of("ITALIAN"))), Set.of(), List.of()),
                "containsTomato", new AnalysisQuery(Map.of(), Set.of("tomato"), List.of()),
                "knownFreeOfSupportedAllergens", new AnalysisQuery(
                        Map.of("allergen", new AnalysisQuery.DimensionFilter(
                                DimensionValue.State.KNOWN, Set.of(), supportedAllergens)), Set.of(), List.of()));
        expected.properties().forEach(entry -> {
            var result = engine.analyze(population, queries.get(entry.getKey()));
            assertEquals(strings(entry.getValue(), "recordIds"), result.selectedRecordIds());
            assertMetrics(entry.getValue().path("metrics"), result.metrics());
        });
    }

    private void assertGarlicLeverage(JsonNode expected, AnalysisPopulation population) {
        var leverage = engine.leverage(population, AnalysisQuery.unfiltered(), "garlic", Set.of("cuisine", "diet"));

        assertEquals(expected.path("recordCount").asLong(), leverage.recordCount());
        assertEquals(strings(expected, "knownCuisineValues"), leverage.knownDimensionValues().get("cuisine"));
        assertEquals(strings(expected, "knownDietValues"), leverage.knownDimensionValues().get("diet"));
        assertEquals(expected.path("knownCuisineCount").asInt(), leverage.knownDimensionValues().get("cuisine").size());
        assertEquals(expected.path("knownDietCount").asInt(), leverage.knownDimensionValues().get("diet").size());
    }

    private void assertGarlicScenario(JsonNode expected, AnalysisPopulation population) {
        var impact = engine.compareElementUnavailability(
                population,
                new AnalysisQuery(Map.of(), Set.of(), List.of("cuisine")),
                new ElementUnavailabilityScenario(strings(expected, "unavailableElementIds")));

        assertEquals(strings(expected, "remainingRecordIds"), impact.scenarioResult().selectedRecordIds());
        assertEquals(strings(expected, "lostRecordIds"), impact.lostRecordIds());
        assertEquals(variants(expected.path("lostStructuralVariants")), impact.lostVariants());
        assertEquals(strings(expected, "lostElementIds"), impact.lostElementIds());
        assertMetrics(expected.path("baselineMetrics"), impact.baseline().metrics());
        assertMetrics(expected.path("scenarioMetrics"), impact.scenarioResult().metrics());
        assertEquals(metricsDelta(expected.path("absoluteDelta")), impact.rootDelta());
        assertRelativeMetricsDelta(expected.path("relativeDelta"), impact.relativeDelta());

        expected.path("cuisineRecordCountDelta").properties().forEach(entry -> {
            var path = new AnalysisResult.GroupPath(List.of(new AnalysisResult.GroupKey(
                    "cuisine", groupValue(entry.getKey()))));
            assertEquals(entry.getValue().asLong(), impact.groupDeltas().get(path).recordCount());
        });
    }

    private static void assertMetrics(JsonNode expected, AnalysisResult.Metrics actual) {
        assertEquals(expected.path("recordCount").asLong(), actual.recordCount());
        assertEquals(expected.path("variantCount").asLong(), actual.variantCount());
        assertEquals(expected.path("uniqueElementCount").asLong(), actual.uniqueElementCount());
    }

    private static List<FoodServiceFixtureAdapter.RejectedRecord> expectedRejectedRecords(JsonNode expected) {
        return StreamSupport.stream(expected.spliterator(), false)
                .map(record -> new FoodServiceFixtureAdapter.RejectedRecord(
                        text(record, "sourceRecordId"), text(record, "reason"), text(record, "detail")))
                .toList();
    }

    private static Set<String> recordIds(JsonNode records) {
        return StreamSupport.stream(records.spliterator(), false)
                .map(record -> text(record, "id"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static DimensionValue expectedDimensionValue(JsonNode expected) {
        return expected.isNull() ? DimensionValue.unknown() : DimensionValue.known(strings(expected));
    }

    private static ImpactResult.MetricsDelta metricsDelta(JsonNode expected) {
        return new ImpactResult.MetricsDelta(
                expected.path("recordCount").asLong(),
                expected.path("variantCount").asLong(),
                expected.path("uniqueElementCount").asLong());
    }

    private static void assertRelativeMetricsDelta(JsonNode expected, ImpactResult.RelativeMetricsDelta actual) {
        assertEquals(expected.path("recordCount").asDouble(), actual.recordCount(), 0.0000000001);
        assertEquals(expected.path("variantCount").asDouble(), actual.variantCount(), 0.0000000001);
        assertEquals(expected.path("uniqueElementCount").asDouble(), actual.uniqueElementCount(), 0.0000000001);
    }

    private static Set<Variant> variants(JsonNode expected) {
        return StreamSupport.stream(expected.spliterator(), false)
                .map(FoodServiceFixtureConformanceTest::strings)
                .map(Variant::new)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String groupValueKey(AnalysisResult.GroupValue value) {
        return switch (value.kind()) {
            case VALUE -> value.value();
            case KNOWN_EMPTY -> "KNOWN_EMPTY";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private static AnalysisResult.GroupValue groupValue(String value) {
        return switch (value) {
            case "KNOWN_EMPTY" -> AnalysisResult.GroupValue.knownEmpty();
            case "UNKNOWN" -> AnalysisResult.GroupValue.unknown();
            default -> AnalysisResult.GroupValue.value(value);
        };
    }

    private JsonNode expectedResults() throws IOException {
        return objectMapper.readTree(Files.readAllBytes(fixtureDirectory().resolve("expected/results.json")));
    }

    private static Path fixtureDirectory() {
        return Path.of("../../../examples/food-service/mvp-v1").toAbsolutePath().normalize();
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (var path : paths.toList()) {
                var target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static Map<String, Long> longMap(JsonNode object) {
        var result = new HashMap<String, Long>();
        object.properties().forEach(entry -> result.put(entry.getKey(), entry.getValue().asLong()));
        return Map.copyOf(result);
    }

    private static Set<String> stringsFromFieldNames(JsonNode object) {
        var result = new java.util.TreeSet<String>();
        object.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static Set<String> strings(JsonNode object, String field) {
        return strings(object.path(field));
    }

    private static Set<String> strings(JsonNode array) {
        var result = new java.util.TreeSet<String>();
        array.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static String text(JsonNode object, String field) {
        return object.path(field).asText();
    }
}
