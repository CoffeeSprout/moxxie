package com.coffeesprout.api.filter;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

@QuarkusTest
class ApiKeyAuthenticationFilterDisabledTest {

    @Test
    void whenAuthDisabledRequestPassesWithoutHeader() {
        RestAssured.given()
            .when()
            .get("/filter-test/ping")
            .then()
            .statusCode(200)
            .body(is("pong"));
    }
}
