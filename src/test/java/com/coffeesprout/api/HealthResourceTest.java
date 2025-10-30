package com.coffeesprout.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class HealthResourceTest {

    @Test
    void testHealthEndpoint() {
        given()
            .when().get("/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("location", notNullValue())
                .body("location.provider", equalTo("proxmox"))
                .body("location.region", equalTo("test-region"))
                .body("location.datacenter", equalTo("test-dc"))
                .body("location.name", equalTo("Test Datacenter"))
                .body("location.coordinates", notNullValue())
                .body("location.coordinates.latitude", equalTo(52.3676F))
                .body("location.coordinates.longitude", equalTo(4.9041F))
                .body("instance_id", equalTo("moxxie-test-instance"));
    }

    @Test
    void testHealthEndpointHeaders() {
        given()
            .when().get("/health")
            .then()
                .statusCode(200)
                .header("X-Moxxie-Location", equalTo("test-region/test-dc"))
                .header("X-Moxxie-Provider", equalTo("proxmox"))
                .header("X-Moxxie-Instance-Id", equalTo("moxxie-test-instance"));
    }
}
