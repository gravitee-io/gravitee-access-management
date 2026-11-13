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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.exception.AuthorizationDetailApprovalNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class UserAuthorizationDetailConsentResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String USER_ID = "user-id-1";
    private static final String CONSENT_ID = "consent-id-1";

    private Domain domain;

    @BeforeEach
    void setUpDomain() {
        domain = new Domain();
        domain.setId(DOMAIN_ID);
        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        reset(authorizationDetailApprovalAdapter);
    }

    @Test
    void shouldRevokeAConsent() {
        doReturn(Completable.complete()).when(authorizationDetailApprovalAdapter).revokeUserConsent(eq(domain), eq(USER_ID), eq(CONSENT_ID), any(User.class));

        Response response = consent().request().delete();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.NO_CONTENT_204);
        verify(authorizationDetailApprovalAdapter).revokeUserConsent(eq(domain), eq(USER_ID), eq(CONSENT_ID), any(User.class));
    }

    @Test
    void shouldNotRevokeAConsentWithoutPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        Response response = consent().request().delete();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.FORBIDDEN_403);
        verify(authorizationDetailApprovalAdapter, never()).revokeUserConsent(any(), any(), any(), any());
    }

    @Test
    void shouldReturnNotFoundForAMissingConsent() {
        doReturn(Completable.error(new AuthorizationDetailApprovalNotFoundException(CONSENT_ID)))
                .when(authorizationDetailApprovalAdapter).revokeUserConsent(eq(domain), eq(USER_ID), eq(CONSENT_ID), any(User.class));

        Response response = consent().request().delete();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.NOT_FOUND_404);
    }

    @Test
    void shouldReturnAnInternalErrorWhenRevokingFails() {
        doReturn(Completable.error(new TechnicalManagementException("unavailable")))
                .when(authorizationDetailApprovalAdapter).revokeUserConsent(eq(domain), eq(USER_ID), eq(CONSENT_ID), any(User.class));

        Response response = consent().request().delete();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR_500);
    }

    private WebTarget consent() {
        return target("domains").path(DOMAIN_ID).path("users").path(USER_ID).path("consents").path("authorization-details").path(CONSENT_ID);
    }
}
