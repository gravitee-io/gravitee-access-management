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
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

    private Response get() {
        return target("domains").path(DOMAIN_ID).path("authorization-detail-types").path(TYPE_ID).request().get();
    }

    private void stubDomain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
    }
}
