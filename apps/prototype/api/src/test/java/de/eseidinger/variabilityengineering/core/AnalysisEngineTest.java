package de.eseidinger.variabilityengineering.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisEngineTest {

    private final AnalysisEngine engine = new AnalysisEngine();

    @Test
    void calculatesDistinctVariantsAndOverlappingDimensionGroups() {
        var result = engine.analyze(population(), new AnalysisQuery(Map.of(), Set.of(), List.of("diet")));

        assertEquals(4, result.metrics().recordCount());
        assertEquals(3, result.metrics().variantCount());
        assertEquals(4, result.metrics().uniqueElementCount());
        assertEquals(3, result.metrics().elementRecordCounts().get("garlic"));
        assertEquals(0.75, result.metrics().elementRecordFrequencies().get("garlic"));

        assertEquals(4, result.groups().size());
        assertEquals(2, group(result, "diet", AnalysisResult.GroupValue.value("VEGAN")).metrics().recordCount());
        assertEquals(2, group(result, "diet", AnalysisResult.GroupValue.value("VEGETARIAN")).metrics().recordCount());
        assertEquals(1, group(result, "diet", AnalysisResult.GroupValue.knownEmpty()).metrics().recordCount());
        assertEquals(1, group(result, "diet", AnalysisResult.GroupValue.unknown()).metrics().recordCount());
    }

    @Test
    void filtersKnownValuesAndDoesNotTreatUnknownAsMatchingAnExclusion() {
        var vegan = engine.analyze(population(), new AnalysisQuery(
                Map.of("diet", AnalysisQuery.DimensionFilter.including(Set.of("VEGAN"))),
                Set.of(),
                List.of()));
        var excludesVegan = engine.analyze(population(), new AnalysisQuery(
                Map.of("diet", new AnalysisQuery.DimensionFilter(null, Set.of(), Set.of("VEGAN"))),
                Set.of(),
                List.of()));

        assertEquals(2, vegan.metrics().recordCount());
        assertEquals(1, vegan.metrics().variantCount());
        assertEquals(2, vegan.metrics().uniqueElementCount());
        assertEquals(1, excludesVegan.metrics().recordCount());
        assertEquals(1, excludesVegan.metrics().variantCount());
        assertFalse(excludesVegan.metrics().elementRecordCounts().containsKey("soy"));
    }

    @Test
    void keepsRootMetricsStableWhenGroupingDimensionsAreReordered() {
        var cuisineThenDiet = engine.analyze(population(), new AnalysisQuery(Map.of(), Set.of(), List.of("cuisine", "diet")));
        var dietThenCuisine = engine.analyze(population(), new AnalysisQuery(Map.of(), Set.of(), List.of("diet", "cuisine")));

        assertEquals(cuisineThenDiet.metrics(), dietThenCuisine.metrics());
        assertEquals("cuisine", cuisineThenDiet.groups().getFirst().path().keys().getFirst().dimensionId());
        assertEquals("diet", dietThenCuisine.groups().getFirst().path().keys().getFirst().dimensionId());
    }

    @Test
    void calculatesElementLeverageOnlyFromKnownDimensionValues() {
        var leverage = engine.leverage(population(), AnalysisQuery.unfiltered(), "garlic", Set.of("cuisine", "diet"));

        assertEquals(3, leverage.recordCount());
        assertEquals(Set.of("ITALIAN", "MEDITERRANEAN"), leverage.knownDimensionValues().get("cuisine"));
        assertEquals(Set.of("VEGAN", "VEGETARIAN"), leverage.knownDimensionValues().get("diet"));
    }

    @Test
    void comparesElementUnavailabilityWithoutMutatingTheBaseline() {
        var impact = engine.compareElementUnavailability(
                population(),
                new AnalysisQuery(Map.of(), Set.of(), List.of("cuisine")),
                new ElementUnavailabilityScenario(Set.of("garlic")));

        assertEquals(4, impact.baseline().metrics().recordCount());
        assertEquals(1, impact.scenarioResult().metrics().recordCount());
        assertEquals(Set.of("record-1", "record-2", "record-3"), impact.lostRecordIds());
        assertEquals(2, impact.lostVariants().size());
        assertEquals(Set.of("garlic", "tomato"), impact.lostElementIds());
        assertEquals(new ImpactResult.MetricsDelta(-3, -2, -2), impact.rootDelta());
        assertEquals(-0.75, impact.relativeDelta().recordCount());
        assertEquals(-2.0 / 3.0, impact.relativeDelta().variantCount());
        assertEquals(-0.5, impact.relativeDelta().uniqueElementCount());
        assertTrue(impact.groupDeltas().values().stream()
                .anyMatch(delta -> delta.equals(new ImpactResult.MetricsDelta(-1, -1, -2))));
    }

    @Test
    void returnsNullRelativeDeltasWhenTheBaselineIsEmpty() {
        var emptyQuery = new AnalysisQuery(
                Map.of("cuisine", AnalysisQuery.DimensionFilter.including(Set.of("NOT_PRESENT"))),
                Set.of(), List.of());

        var impact = engine.compareElementUnavailability(
                population(), emptyQuery, new ElementUnavailabilityScenario(Set.of("garlic")));

        assertEquals(new ImpactResult.RelativeMetricsDelta(null, null, null), impact.relativeDelta());
    }

    @Test
    void rejectsDimensionAndScenarioContractViolations() {
        assertThrows(IllegalArgumentException.class, () -> new DimensionDefinition(
                "cuisine", DimensionDefinition.Cardinality.SINGLE, DimensionDefinition.Origin.IMPORTED)
                .validate(DimensionValue.known(Set.of("ITALIAN", "MEDITERRANEAN"))));
        assertThrows(IllegalArgumentException.class, () -> engine.compareElementUnavailability(
                population(), AnalysisQuery.unfiltered(), new ElementUnavailabilityScenario(Set.of("unknown"))));
        assertThrows(IllegalArgumentException.class, () -> engine.leverage(
                population(), AnalysisQuery.unfiltered(), "unknown", Set.of("cuisine")));
        assertThrows(IllegalArgumentException.class, () -> engine.analyze(
                population(), new AnalysisQuery(Map.of("unknown", AnalysisQuery.DimensionFilter.any()), Set.of(), List.of())));
    }

    private static AnalysisResult.Group group(AnalysisResult result, String dimensionId, AnalysisResult.GroupValue value) {
        return result.groups().stream()
                .filter(group -> group.path().keys().equals(List.of(new AnalysisResult.GroupKey(dimensionId, value))))
                .findFirst()
                .orElseThrow();
    }

    private static AnalysisPopulation population() {
        var provenance = Map.of("fixture", new AnalysisPopulation.ArtifactVersion("fixture", "1", "0".repeat(64)));
        var dimensions = Map.of(
                "cuisine", new DimensionDefinition("cuisine", DimensionDefinition.Cardinality.SINGLE, DimensionDefinition.Origin.IMPORTED),
                "diet", new DimensionDefinition("diet", DimensionDefinition.Cardinality.MULTI, DimensionDefinition.Origin.DERIVED));
        return new AnalysisPopulation("test", "1", provenance, dimensions, List.of(
                record("record-1", Set.of("tomato", "garlic"), "ITALIAN", DimensionValue.known(Set.of("VEGAN", "VEGETARIAN"))),
                record("record-2", Set.of("garlic", "tomato"), "MEDITERRANEAN", DimensionValue.known(Set.of("VEGAN", "VEGETARIAN"))),
                record("record-3", Set.of("rice", "garlic"), "MEDITERRANEAN", DimensionValue.known(Set.of())),
                record("record-4", Set.of("rice", "soy"), null, DimensionValue.unknown())));
    }

    private static VariantRecord record(String id, Set<String> elements, String cuisine, DimensionValue diet) {
        return new VariantRecord(id, new Variant(elements), Map.of(
                "cuisine", cuisine == null ? DimensionValue.unknown() : DimensionValue.known(Set.of(cuisine)),
                "diet", diet));
    }
}
