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

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class ApplicationSecretResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-a";
    private static final String APPLICATION_ID = "app-1";
    private static final String SECRET_ID = "secret-1";

    @BeforeEach
    public void resetSecretService() {
        reset(applicationSecretService);
    }

    @Test
    public void shouldNotRenewSecret_applicationFromAnotherDomain() {
        doReturn(Maybe.just(domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(applicationService).findById(DOMAIN_ID, APPLICATION_ID);

        final Response response = secretTarget().path("_renew").request().post(Entity.json(null));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(applicationSecretService, never()).renew(any(), any(), any(), any());
    }

    @Test
    public void shouldNotDeleteSecret_applicationFromAnotherDomain() {
        doReturn(Maybe.just(domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(applicationService).findById(DOMAIN_ID, APPLICATION_ID);

        final Response response = secretTarget().request().delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(applicationSecretService, never()).delete(any(), any(), any(), any());
    }

    private WebTarget secretTarget() {
        return target("domains").path(DOMAIN_ID).path("applications").path(APPLICATION_ID).path("secrets").path(SECRET_ID);
    }

    private static Domain domain() {
        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        return domain;
    }
}
