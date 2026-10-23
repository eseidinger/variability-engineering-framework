package de.eseidinger.variabilityengineering.http;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class FoodServiceAnalysisResourceTest {

    private static final String POPULATION = "/api/populations/food-service-mvp";

    @Test
    void returnsTheFixturePopulationSummaryAndRecordDetail() {
        given()
                .when().get(POPULATION)
                .then()
                .statusCode(200)
                .body("population.id", is("food-service-mvp"))
                .body("population.version", is("1.0.0"))
                .body("population.provenance.mapping.id", is("food-service-mvp-ingredient-aliases"))
                .body("population.provenance.sourceDataset.sha256", is("5bd64d1e3369b3eb98434fcd9d65475916a69dd1eb94078056cd45dc7be9cdd2"))
                .body("metrics.recordCount", is(12))
                .body("metrics.variantCount", is(11))
                .body("metrics.uniqueElementCount", is(24))
                .body("dimensions.cuisine.observedValues", containsInAnyOrder(
                        "EAST_ASIAN", "EUROPEAN", "INDIAN", "ITALIAN", "LATIN_AMERICAN",
                        "MEDITERRANEAN", "MIDDLE_EASTERN", "SOUTHEAST_ASIAN"))
                .body("rejectedRecords.sourceRecordId", contains("recipe-013"));

        given()
                .when().get(POPULATION + "/records/recipe-011")
                .then()
                .statusCode(200)
                .body("population.version", is("1.0.0"))
                .body("population.provenance.mapping.id", is("food-service-mvp-ingredient-aliases"))
                .body("record.id", is("recipe-011"))
                .body("record.dimensions.cuisine.state", is("UNKNOWN"))
                .body("record.dimensions.allergen.state", is("UNKNOWN"));
    }

    @Test
    void analyzesTheFixtureWithFiltersAndGrouping() {
        var request = """
                {
                  "dimensionFilters": {
                    "diet": {"includedValues": ["VEGAN"]}
                  },
                  "groupingDimensions": ["cuisine"]
                }
                """;

        given()
                .contentType("application/json")
                .body(request)
                .when().post(POPULATION + "/analysis")
                .then()
                .statusCode(200)
                .body("population.id", is("food-service-mvp"))
                .body("population.version", is("1.0.0"))
                .body("population.provenance.mapping.id", is("food-service-mvp-ingredient-aliases"))
                .body("query.dimensionFilters.diet.includedValues", contains("VEGAN"))
                .body("query.groupingDimensions", contains("cuisine"))
                .body("metrics.recordCount", is(8))
                .body("metrics.variantCount", is(7))
                .body("metrics.uniqueElementCount", is(19))
                .body("selectedRecordIds", containsInAnyOrder(
                        "recipe-001", "recipe-002", "recipe-003", "recipe-004",
                        "recipe-006", "recipe-007", "recipe-010", "recipe-012"))
                .body("groups[0].path[0].dimensionId", is("cuisine"));
    }

    @Test
    void returnsLeverageAndElementUnavailabilityImpact() {
        var leverageRequest = """
                {"query": {}, "dimensionIds": ["cuisine", "diet"]}
                """;
        given()
                .contentType("application/json")
                .body(leverageRequest)
                .when().post(POPULATION + "/elements/garlic/leverage")
                .then()
                .statusCode(200)
                .body("population.version", is("1.0.0"))
                .body("population.provenance.mapping.id", is("food-service-mvp-ingredient-aliases"))
                .body("query.requiredElementIds", is(java.util.List.of()))
                .body("elementId", is("garlic"))
                .body("recordCount", is(8))
                .body("knownDimensionValues.cuisine", containsInAnyOrder(
                        "EAST_ASIAN", "INDIAN", "ITALIAN", "MEDITERRANEAN", "MIDDLE_EASTERN"))
                .body("knownDimensionValues.diet", containsInAnyOrder("VEGAN", "VEGETARIAN"));

        var scenarioRequest = """
                {
                  "query": {"groupingDimensions": ["cuisine"]},
                  "unavailableElementIds": ["garlic"]
                }
                """;
        given()
                .contentType("application/json")
                .body(scenarioRequest)
                .when().post(POPULATION + "/element-unavailability")
                .then()
                .statusCode(200)
                .body("population.version", is("1.0.0"))
                .body("population.provenance.mapping.id", is("food-service-mvp-ingredient-aliases"))
                .body("query.groupingDimensions", contains("cuisine"))
                .body("scenario.unavailableElementIds", contains("garlic"))
                .body("baseline.metrics.recordCount", is(12))
                .body("scenarioResult.metrics.recordCount", is(4))
                .body("lostRecordIds", hasItems("recipe-001", "recipe-011"))
                .body("rootDelta.recordCount", is(-8))
                // RestAssured deserializes JSON decimal values as floats by default.
                .body("relativeDelta.recordCount", is(-0.6666667f));
    }

    @Test
    void reportsUnknownPopulationAndInvalidAnalysisInput() {
        given()
                .when().get("/api/populations/unknown")
                .then()
                .statusCode(404);

        given()
                .contentType("application/json")
                .body("{\"requiredElementIds\":[\"unknown\"]}")
                .when().post(POPULATION + "/analysis")
                .then()
                .statusCode(400);
    }

    @Test
    void doesNotUseClientSuppliedGroupHeaders() {
        given()
                .when().get(POPULATION)
                .then().statusCode(200);

        given().header("X-Platform-Principal-Group-Ids", "untrusted-client-value")
                .contentType("application/json")
                .body("{}")
                .when().post(POPULATION + "/analysis")
                .then().statusCode(200);
    }
}
