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
import io.gravitee.am.service.exception.AuthorizationDetailTypeNotFoundException;
import io.gravitee.am.service.exception.InvalidAuthorizationDetailTypeException;
import io.gravitee.am.service.model.PatchAuthorizationDetailType;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.glassfish.jersey.client.HttpUrlConnectorProvider.SET_METHOD_WORKAROUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class AuthorizationDetailTypeResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-id";
    private static final String TYPE_ID = "adt-1";

    @BeforeEach
    void resetService() {
        reset(authorizationDetailTypeService);
    }

    @Test
    void shouldRead() {
        stubDomain();
        doReturn(Maybe.just(AuthorizationDetailType.builder()
                .id(TYPE_ID)
                .domain(DOMAIN_ID)
                .type("payment_initiation")
                .name("Payment initiation")
                .build()))
                .when(authorizationDetailTypeService).findById(DOMAIN_ID, TYPE_ID);

        final Response response = get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        Map<String, Object> body = response.readEntity(Map.class);
        assertEquals(TYPE_ID, body.get("id"));
        assertEquals("payment_initiation", body.get("type"));
    }

    @Test
    void shouldReturnNotFoundWhenTypeIsMissingOrInAnotherDomain() {
        stubDomain();
        doReturn(Maybe.empty()).when(authorizationDetailTypeService).findById(DOMAIN_ID, TYPE_ID);

        final Response response = get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    void shouldReturnNotFoundWhenDomainDoesNotExist() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(authorizationDetailTypeService, never()).findById(any(), any());
    }

    @Test
    void shouldRejectCallerWithoutPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = get();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(authorizationDetailTypeService, never()).findById(any(), any());
    }

    @Test
    void shouldRejectCallerHoldingOnlyTheScopePermission() {
        grantOnly(DOMAIN_ID, Permission.DOMAIN_SCOPE);

        final Response response = get();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    void shouldPatch() {
        Domain domain = stubDomain();
        doReturn(Single.just(AuthorizationDetailType.builder()
                .id(TYPE_ID)
                .domain(DOMAIN_ID)
                .type("payment_initiation")
                .name("Renamed")
                .build()))
                .when(authorizationDetailTypeService).patch(eq(domain), eq(TYPE_ID), any(), any());

        final Response response = patch("{\"name\":\"Renamed\",\"description\":null}");

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        Map<String, Object> body = response.readEntity(Map.class);
        assertEquals(TYPE_ID, body.get("id"));
        assertEquals("Renamed", body.get("name"));
        PatchAuthorizationDetailType sent = sentPatch(domain);
        assertEquals(Optional.of("Renamed"), sent.getName());
        assertEquals(Optional.empty(), sent.getDescription());
        assertNull(sent.getSchema());
        assertNull(sent.getDiscovery());
        assertNull(sent.getExpiresIn());
    }

    @Test
    void shouldRejectTypeInPatch() {
        final Response response = patch("{\"type\":\"account_information\",\"name\":\"Renamed\"}");

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Property [type] is not recognized"));
        verify(authorizationDetailTypeService, never()).patch(any(), any(), any(), any());
    }

    @Test
    void shouldRejectClearingName() {
        final Response response = patch("{\"name\":null}");

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Name is required"));
        verify(authorizationDetailTypeService, never()).patch(any(), any(), any(), any());
    }

    @Test
    void shouldRejectBlankName() {
        final Response response = patch("{\"name\":\"  \"}");

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Name is required"));
    }

    @Test
    void shouldRejectClearingDiscovery() {
        final Response response = patch("{\"discovery\":null}");

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Discovery can't be cleared"));
        verify(authorizationDetailTypeService, never()).patch(any(), any(), any(), any());
    }

    @Test
    void shouldRejectPatchedExpiresInBelowOne() {
        final Response response = patch("{\"expiresIn\":0}");

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Expires in must be greater than 0"));
    }

    @Test
    void shouldReturnServiceValidationErrorOnPatch() {
        Domain domain = stubDomain();
        doReturn(Single.error(new InvalidAuthorizationDetailTypeException("Schema must be a JSON object")))
                .when(authorizationDetailTypeService).patch(eq(domain), eq(TYPE_ID), any(), any());

        final Response response = patch("{\"schema\":\"[]\"}");

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Schema must be a JSON object"));
    }

    @Test
    void shouldReturnNotFoundWhenPatchedTypeIsMissingOrInAnotherDomain() {
        Domain domain = stubDomain();
        doReturn(Single.error(new AuthorizationDetailTypeNotFoundException(TYPE_ID)))
                .when(authorizationDetailTypeService).patch(eq(domain), eq(TYPE_ID), any(), any());

        final Response response = patch("{\"name\":\"Renamed\"}");

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    void shouldRejectPatchWithoutPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = patch("{\"name\":\"Renamed\"}");

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(authorizationDetailTypeService, never()).patch(any(), any(), any(), any());
    }

    @Test
    void shouldRejectPatchHoldingOnlyTheScopePermission() {
        grantOnly(DOMAIN_ID, Permission.DOMAIN_SCOPE);

        final Response response = patch("{\"name\":\"Renamed\"}");

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    void shouldDelete() {
        Domain domain = stubDomain();
        doReturn(Completable.complete()).when(authorizationDetailTypeService).delete(eq(domain), eq(TYPE_ID), any());

        final Response response = delete();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
        verify(authorizationDetailTypeService).delete(eq(domain), eq(TYPE_ID), any());
    }

    @Test
    void shouldReturnNotFoundWhenDeletedTypeIsMissingOrInAnotherDomain() {
        Domain domain = stubDomain();
        doReturn(Completable.error(new AuthorizationDetailTypeNotFoundException(TYPE_ID)))
                .when(authorizationDetailTypeService).delete(eq(domain), eq(TYPE_ID), any());

        final Response response = delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingInADomainThatDoesNotExist() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(authorizationDetailTypeService, never()).delete(any(), any(), any());
    }

    @Test
    void shouldRejectDeleteWithoutPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = delete();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(authorizationDetailTypeService, never()).delete(any(), any(), any());
    }

    @Test
    void shouldRejectDeleteHoldingOnlyTheScopePermission() {
        grantOnly(DOMAIN_ID, Permission.DOMAIN_SCOPE);

        final Response response = delete();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    private Response get() {
        return target("domains").path(DOMAIN_ID).path("authorization-detail-types").path(TYPE_ID).request().get();
    }

    private Response patch(String json) {
        return target("domains").path(DOMAIN_ID).path("authorization-detail-types").path(TYPE_ID)
                .request()
                .property(SET_METHOD_WORKAROUND, true)
                .method(HttpMethod.PATCH, Entity.json(json));
    }

    private Response delete() {
        return target("domains").path(DOMAIN_ID).path("authorization-detail-types").path(TYPE_ID).request().delete();
    }

    private PatchAuthorizationDetailType sentPatch(Domain domain) {
        ArgumentCaptor<PatchAuthorizationDetailType> patch = ArgumentCaptor.forClass(PatchAuthorizationDetailType.class);
        verify(authorizationDetailTypeService).patch(eq(domain), eq(TYPE_ID), patch.capture(), any());
        return patch.getValue();
    }

    private Domain stubDomain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        return domain;
    }
}
