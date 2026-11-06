package com.redhat.coolstore.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Smoke tests: the Quarkus application boots, the microprofile liveness
 * probe answers and the static SPA index is served.
 */
@QuarkusTest
class HealthSmokeTest {

    @Test
    @DisplayName("GET /q/health/live -> 200 UP")
    void livenessProbeIsUp() {
        given()
                .when().get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("GET / -> 200 (static index.html)")
    void indexHtmlIsServed() {
        given()
                .when().get("/")
                .then()
                .statusCode(200);
    }
}
