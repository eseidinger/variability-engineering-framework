package de.eseidinger.variabilityengineering;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.http.TestHTTPResource;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class StatusResourceTest {

    @TestHTTPResource(value = "/metrics", management = true)
    URL metricsUrl;

    @Test
    void returnsApiStatus() {
        given()
          .when().get("/api/status")
          .then()
             .statusCode(200)
             .contentType("text/plain")
             .body(is("Variability Engineering API and population store are ready"));
    }

    @Test
    void exposesHttpMetricsOnManagementPort() {
        given().get("/api/status");

        given()
          .when().get(metricsUrl.toString())
          .then()
             .statusCode(200)
             .body(containsString("http_server_requests_seconds_count"));
    }
}
