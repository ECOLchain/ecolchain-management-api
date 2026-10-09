package com.ecolchain.api;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DevDocumentResourceProfileGateTest {

    @Test
    void documentsEndpointsAreNotMountedOutsideDevProfile() {
        given().when().get("/documents/upload-url").then().statusCode(404);
        given().when().get("/documents/docs/x/download-url").then().statusCode(404);
    }
}
