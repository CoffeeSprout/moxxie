package com.coffeesprout.api.filter;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestProfile(ApiKeyAuthEnabledProfile.class)
class ApiKeyAuthenticationFilterEnabledTest {

    @Test
    void missingApiKeyReturnsUnauthorized() {
        RestAssured.given()
            .when()
            .get("/filter-test/ping")
            .then()
            .statusCode(401)
            .body("error", is("UNAUTHORIZED"))
            .body("message", containsString("Missing API key"));
    }

    @Test
    void invalidApiKeyReturnsUnauthorized() {
        RestAssured.given()
            .header("X-API-Key", "wrong")
            .when()
            .get("/filter-test/ping")
            .then()
            .statusCode(401)
            .body("error", is("UNAUTHORIZED"))
            .body("message", containsString("Invalid API key"));
    }

    @Test
    void validApiKeyAllowsRequest() {
        RestAssured.given()
            .header("X-API-Key", "test-secret")
            .when()
            .get("/filter-test/ping")
            .then()
            .statusCode(200)
            .body(is("pong"));
    }
}
