package com.ecolchain.api.contract;

import com.ecolchain.api.catalog.application.port.out.CatalogStore;
import com.ecolchain.api.catalog.domain.ProfileType;
import com.ecolchain.api.identity.application.port.out.AccountStore;
import com.ecolchain.api.identity.domain.Account;
import com.ecolchain.api.onboarding.application.port.out.CompanyStore;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
class PublicAndAdminTest {

    @InjectMock CatalogStore catalog;
    @InjectMock AccountStore accounts;
    @InjectMock CompanyStore companies;

    private ProfileType type(String code, int pos) {
        var p = new ProfileType();
        p.id = UUID.randomUUID();
        p.code = code; p.namePt = "Nome " + code; p.nameEn = "Name " + code;
        p.active = true; p.position = pos;
        return p;
    }

    @BeforeEach
    void reset() {
        Mockito.reset(catalog, accounts, companies);
        Mockito.when(catalog.listProfileTypes(true))
                .thenReturn(List.of(type("GERADOR", 10), type("RECICLADORA", 50)));
    }

    @Test
    void publicProfileTypesNeedNoAuth() {
        given().when().get("/public/profile-types")
                .then().statusCode(200)
                .body("data.tipos.size()", is(2))
                .body("data.tipos[0].codigo", is("GERADOR"))
                .body("data.tipos[0].nome", is("Nome GERADOR"))
                .body("erros.size()", is(0));
    }

    @Test
    void publicProfileTypesLocalizedToEn() {
        given().header("Accept-Language", "en")
                .when().get("/public/profile-types")
                .then().statusCode(200)
                .body("data.tipos[0].nome", is("Name GERADOR"));
    }

    @Test
    void adminRouteWithoutTokenIs401() {
        given().when().get("/admin/companies")
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "11111111-1111-4111-8111-111111111111", roles = "COMPANY_OWNER")
    void adminRouteWithOwnerRoleIs403() {
        var acc = new Account();
        acc.id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        acc.email = "dono@empresa.com";
        acc.role = Account.Role.COMPANY_OWNER;
        acc.status = Account.Status.ACTIVE;
        Mockito.when(accounts.byId(acc.id)).thenReturn(Optional.of(acc));
        given().when().get("/admin/companies")
                .then().statusCode(403)
                .body("erros[0].code", is("FORBIDDEN"));
    }

    @Test
    @TestSecurity(user = "22222222-2222-4222-8222-222222222222", roles = "PLATFORM_ADMIN")
    void adminRouteWithAdminRoleLists() {
        var acc = new Account();
        acc.id = UUID.fromString("22222222-2222-4222-8222-222222222222");
        acc.email = "admin@ecolchain.com";
        acc.role = Account.Role.PLATFORM_ADMIN;
        acc.status = Account.Status.ACTIVE;
        Mockito.when(accounts.byId(acc.id)).thenReturn(Optional.of(acc));
        Mockito.when(companies.listByStatus(null, 0, 20)).thenReturn(List.of());
        given().when().get("/admin/companies")
                .then().statusCode(200)
                .body("data.empresas.size()", is(0))
                .body("data.total", is(0));
    }
}
