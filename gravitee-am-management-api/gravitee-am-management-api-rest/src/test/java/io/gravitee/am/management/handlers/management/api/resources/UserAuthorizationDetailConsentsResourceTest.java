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
package io.gravitee.am.management.handlers.management.api.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.handlers.management.api.model.AuthorizationDetailApprovalEntity;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserAuthorizationDetailConsentsResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String USER_ID = "user-id-1";

    private Domain domain;

    @BeforeEach
    void setUpDomain() {
        domain = new Domain();
        domain.setId(DOMAIN_ID);
        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
    }

    @Test
    void shouldListTheConsents() {
        AuthorizationDetailApproval approval = new AuthorizationDetailApproval();
        approval.setUserId(UserId.internal(USER_ID));
        approval.setClientId("client-id");
        approval.setDetail(Map.of("type", "payment_initiation"));
        doReturn(Single.just(List.of(new AuthorizationDetailApprovalEntity(approval))))
                .when(authorizationDetailApprovalAdapter).getUserConsents(domain, USER_ID, null);

        Response response = consents().request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.OK_200);
        List<Map<String, Object>> body = readEntity(response, new TypeReference<>() {});
        assertThat(body).singleElement().satisfies(consent -> {
            assertThat(consent).containsEntry("type", "payment_initiation").containsEntry("clientId", "client-id");
            assertThat(consent.get("detail")).isEqualTo(Map.of("type", "payment_initiation"));
        });
    }

    @Test
    void shouldPassTheClientFilter() {
        doReturn(Single.just(List.of())).when(authorizationDetailApprovalAdapter).getUserConsents(domain, USER_ID, "client-id");

        Response response = consents().queryParam("clientId", "client-id").request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.OK_200);
        verify(authorizationDetailApprovalAdapter).getUserConsents(domain, USER_ID, "client-id");
    }

    @Test
    void shouldRejectAUserWithoutPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        Response response = consents().request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.FORBIDDEN_403);
        verify(authorizationDetailApprovalAdapter, never()).getUserConsents(any(), any(), any());
    }

    @Test
    void shouldReturnNotFoundForAnUnknownUser() {
        doReturn(Single.error(new UserNotFoundException(UserId.internal(USER_ID))))
                .when(authorizationDetailApprovalAdapter).getUserConsents(eq(domain), eq(USER_ID), isNull());

        Response response = consents().request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.NOT_FOUND_404);
    }

    @Test
    void shouldReturnAnInternalErrorOnTechnicalFailure() {
        doReturn(Single.error(new TechnicalManagementException("unavailable")))
                .when(authorizationDetailApprovalAdapter).getUserConsents(eq(domain), eq(USER_ID), isNull());

        Response response = consents().request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR_500);
    }

    private WebTarget consents() {
        return target("domains").path(DOMAIN_ID).path("users").path(USER_ID).path("consents").path("authorization-details");
    }
}
