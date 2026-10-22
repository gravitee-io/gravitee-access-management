/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.AuthorizationDetailTypeAlreadyExistsException;
import io.gravitee.am.service.exception.InvalidAuthorizationDetailTypeException;
import io.gravitee.am.service.model.NewAuthorizationDetailType;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * @author GraviteeSource Team
 */
class AuthorizationDetailTypesResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-id";

    @BeforeEach
    void resetService() {
        reset(authorizationDetailTypeService);
    }

    @Test
    void shouldCreate() {
        Domain domain = stubDomain();
        AuthorizationDetailType created = AuthorizationDetailType.builder()
                .id("adt-1")
                .domain(DOMAIN_ID)
                .type("payment_initiation")
                .name("Payment initiation")
                .schema("{\"type\":\"object\"}")
                .discovery(true)
                .expiresIn(3600)
                .version(1)
                .build();
        doReturn(Single.just(created)).when(authorizationDetailTypeService).create(eq(domain), any(NewAuthorizationDetailType.class), any());

        final Response response = post(Map.of(
                "type", "payment_initiation",
                "name", "Payment initiation",
                "schema", "{\"type\":\"object\"}",
                "discovery", true,
                "expiresIn", 3600));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        assertTrue(response.getLocation().toString()
                .endsWith("/organizations/DEFAULT/environments/DEFAULT/domains/" + DOMAIN_ID + "/authorization-detail-types/adt-1"));
        Map<String, Object> body = response.readEntity(Map.class);
        assertEquals("adt-1", body.get("id"));
        assertEquals(DOMAIN_ID, body.get("domain"));
        assertEquals("payment_initiation", body.get("type"));
        assertEquals("{\"type\":\"object\"}", body.get("schema"));
        assertEquals(true, body.get("discovery"));
        assertEquals(3600, body.get("expiresIn"));
        assertEquals(1, body.get("version"));

        ArgumentCaptor<NewAuthorizationDetailType> input = ArgumentCaptor.forClass(NewAuthorizationDetailType.class);
        verify(authorizationDetailTypeService).create(eq(domain), input.capture(), any());
        assertEquals("payment_initiation", input.getValue().getType());
        assertEquals("Payment initiation", input.getValue().getName());
        assertTrue(input.getValue().isDiscovery());
        assertEquals(3600, input.getValue().getExpiresIn());
    }

    @Test
    void shouldRejectMissingType() {
        stubDomain();

        final Response response = post(Map.of("name", "Payment initiation"));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Type is required"));
        verify(authorizationDetailTypeService, never()).create(any(), any(), any());
    }

    @Test
    void shouldRejectTooLongName() {
        stubDomain();

        final Response response = post(Map.of("type", "payment_initiation", "name", "n".repeat(AuthorizationDetailType.NAME_MAX_LENGTH + 1)));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Name must be at most 255 characters"));
        verify(authorizationDetailTypeService, never()).create(any(), any(), any());
    }

    @Test
    void shouldRejectTypeOutsideVisibleAscii() {
        stubDomain();

        final Response response = post(Map.of("type", "payment initiation", "name", "Payment initiation"));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Type must contain only visible ASCII characters (%x21-7E)"));
        verify(authorizationDetailTypeService, never()).create(any(), any(), any());
    }

    @Test
    void shouldReturnServiceValidationError() {
        Domain domain = stubDomain();
        doReturn(Single.error(new InvalidAuthorizationDetailTypeException("Schema must be a JSON object")))
                .when(authorizationDetailTypeService).create(eq(domain), any(NewAuthorizationDetailType.class), any());

        final Response response = post(Map.of("type", "payment_initiation", "name", "Payment initiation", "schema", "[]"));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Schema must be a JSON object"));
    }

    @Test
    void shouldRejectDuplicate() {
        Domain domain = stubDomain();
        doReturn(Single.error(new AuthorizationDetailTypeAlreadyExistsException("Payment_Initiation", DOMAIN_ID)))
                .when(authorizationDetailTypeService).create(eq(domain), any(NewAuthorizationDetailType.class), any());

        final Response response = post(Map.of("type", "Payment_Initiation", "name", "Payment initiation"));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("already exists"));
    }

    @Test
    void shouldReturnNotFoundWhenDomainDoesNotExist() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = post(Map.of("type", "payment_initiation", "name", "Payment initiation"));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(authorizationDetailTypeService, never()).create(any(), any(), any());
    }

    @Test
    void shouldRejectCallerWithoutPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = post(Map.of("type", "payment_initiation", "name", "Payment initiation"));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(authorizationDetailTypeService, never()).create(any(), any(), any());
    }

    @Test
    void shouldRejectCallerHoldingOnlyTheScopePermission() {
        grantOnly(DOMAIN_ID, Permission.DOMAIN_SCOPE);

        final Response response = post(Map.of("type", "payment_initiation", "name", "Payment initiation"));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(authorizationDetailTypeService, never()).create(any(), any(), any());
    }

    @Test
    void shouldAcceptCallerHoldingTheAuthorizationDetailTypePermission() {
        Domain domain = stubDomain();
        grantOnly(DOMAIN_ID, Permission.DOMAIN_AUTHORIZATION_DETAIL_TYPE);
        doReturn(Single.just(AuthorizationDetailType.builder().id("adt-1").domain(DOMAIN_ID).build()))
                .when(authorizationDetailTypeService).create(eq(domain), any(NewAuthorizationDetailType.class), any());

        final Response response = post(Map.of("type", "payment_initiation", "name", "Payment initiation"));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }

    private Response post(Map<String, Object> body) {
        return target("domains").path(DOMAIN_ID).path("authorization-detail-types").request().post(Entity.json(body));
    }

    private Domain stubDomain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        return domain;
    }
}
