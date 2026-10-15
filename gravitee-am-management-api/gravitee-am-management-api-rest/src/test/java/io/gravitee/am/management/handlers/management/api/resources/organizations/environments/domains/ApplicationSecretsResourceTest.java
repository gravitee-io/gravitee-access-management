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
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.service.model.NewClientSecret;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class ApplicationSecretsResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-a";
    private static final String APPLICATION_ID = "app-1";

    @BeforeEach
    public void resetSecretService() {
        reset(applicationSecretService);
    }

    @Test
    public void shouldCreateSecret() {
        doReturn(Maybe.just(domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(application())).when(applicationService).findById(DOMAIN_ID, APPLICATION_ID);
        final ClientSecret clientSecret = new ClientSecret();
        clientSecret.setId("secret-1");
        clientSecret.setSecret("raw-secret");
        doReturn(Single.just(clientSecret)).when(applicationSecretService).create(any(Domain.class), any(Application.class), any(NewClientSecret.class), any(User.class));

        final Response response = post(secretsTarget(), newClientSecret());

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }

    @Test
    public void shouldNotCreateSecret_applicationFromAnotherDomain() {
        doReturn(Maybe.just(domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(applicationService).findById(DOMAIN_ID, APPLICATION_ID);

        final Response response = post(secretsTarget(), newClientSecret());

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(applicationSecretService, never()).create(any(), any(), any(), any());
    }

    @Test
    public void shouldCheckCreatePermissionOnApplication() {
        doReturn(Maybe.just(domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(applicationService).findById(DOMAIN_ID, APPLICATION_ID);
        reset(permissionService);
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        post(secretsTarget(), newClientSecret());

        final ArgumentCaptor<PermissionAcls> aclsCaptor = ArgumentCaptor.forClass(PermissionAcls.class);
        verify(permissionService).hasPermission(any(User.class), aclsCaptor.capture());
        assertTrue(aclsCaptor.getValue().referenceStream()
                        .anyMatch(ref -> ref.getKey() == ReferenceType.APPLICATION && APPLICATION_ID.equals(ref.getValue())),
                "APPLICATION_OPENID[CREATE] must be checked on the application");
    }

    @Test
    public void shouldListSecrets() {
        doReturn(Maybe.just(domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(application())).when(applicationService).findById(DOMAIN_ID, APPLICATION_ID);
        doReturn(Flowable.empty()).when(applicationSecretService).findAllByApplication(any(Application.class));

        final Response response = secretsTarget().request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldNotListSecrets_applicationFromAnotherDomain() {
        doReturn(Maybe.just(domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(applicationService).findById(DOMAIN_ID, APPLICATION_ID);

        final Response response = secretsTarget().request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(applicationSecretService, never()).findAllByApplication(any());
    }

    private WebTarget secretsTarget() {
        return target("domains").path(DOMAIN_ID).path("applications").path(APPLICATION_ID).path("secrets");
    }

    private static NewClientSecret newClientSecret() {
        final NewClientSecret newClientSecret = new NewClientSecret();
        newClientSecret.setName("secret");
        return newClientSecret;
    }

    private static Domain domain() {
        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        return domain;
    }

    private static Application application() {
        final Application application = new Application();
        application.setId(APPLICATION_ID);
        application.setDomain(DOMAIN_ID);
        return application;
    }
}
