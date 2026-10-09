package com.ecolchain.api.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Envelope obrigatório + Problem RFC 9457 nos erros centrais. */
@QuarkusTest
class ErrorEnvelopeTest {

    @Test
    void unknownRouteReturns404Envelope() {
        given().when().get("/rota-inexistente")
                .then().statusCode(404)
                .body("erros[0].code", is("NOT_FOUND"))
                .body("erros[0].status", is(404))
                .body("links.size()", is(0));
    }

    @Test
    void protectedRouteWithoutTokenReturns401() {
        given().when().get("/me")
                .then().statusCode(401)
                .body("erros[0].code", is("AUTH_REQUIRED"));
    }

    @Test
    void otpRequestWithBadEmailIs400() {
        given().contentType("application/json")
                .body("{\"email\":\"nao-e-email\"}")
                .when().post("/auth/otp/request")
                .then().statusCode(400)
                .body("erros[0].code", is("VALIDATION"))
                .body("erros[0].campo", is("email"));
    }
}
