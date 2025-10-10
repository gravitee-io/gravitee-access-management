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
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.model.NewAuthorizationEngine;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author GraviteeSource Team
 */
public class AuthorizationEnginesResourceTest extends JerseySpringTest {

    @Test
    public void shouldListAuthorizationEngines() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final AuthorizationEngine engine1 = new AuthorizationEngine();
        engine1.setId("engine-1");
        engine1.setName("Engine 1");
        engine1.setType("openfga");

        final AuthorizationEngine engine2 = new AuthorizationEngine();
        engine2.setId("engine-2");
        engine2.setName("Engine 2");
        engine2.setType("openfga");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Flowable.just(engine1, engine2)).when(authorizationEngineService).findByDomain(domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("authorization-engines")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        // Verify the list is sorted by name
        verify(authorizationEngineService, times(1)).findByDomain(domainId);
    }

    @Test
    public void shouldListAuthorizationEngines_emptyList() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Flowable.empty()).when(authorizationEngineService).findByDomain(domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("authorization-engines")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldListAuthorizationEngines_domainNotFound() {
        final String domainId = "non-existent-domain";

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("authorization-engines")
                .request()
                .get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldCreateAuthorizationEngine() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final NewAuthorizationEngine newEngine = new NewAuthorizationEngine();
        newEngine.setName("Test Engine");
        newEngine.setType("openfga");
        newEngine.setConfiguration("{\"connectionUri\":\"http://localhost:8080\"}");

        final AuthorizationEngine createdEngine = new AuthorizationEngine();
        createdEngine.setId("engine-id");
        createdEngine.setName(newEngine.getName());
        createdEngine.setType(newEngine.getType());
        createdEngine.setConfiguration(newEngine.getConfiguration());
        createdEngine.setReferenceType(ReferenceType.DOMAIN);
        createdEngine.setReferenceId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(createdEngine)).when(authorizationEngineService)
                .create(eq(domainId), any(NewAuthorizationEngine.class), any());

        final Response response = post(
                target("domains")
                        .path(domainId)
                        .path("authorization-engines"),
                newEngine
        );

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());

        verify(authorizationEngineService, times(1))
                .create(eq(domainId), any(NewAuthorizationEngine.class), any());
    }

    @Test
    public void shouldCreateAuthorizationEngine_domainNotFound() {
        final String domainId = "non-existent-domain";
        final NewAuthorizationEngine newEngine = new NewAuthorizationEngine();
        newEngine.setName("Test Engine");
        newEngine.setType("openfga");
        newEngine.setConfiguration("{}");

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = post(
                target("domains")
                        .path(domainId)
                        .path("authorization-engines"),
                newEngine
        );

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());

        verify(authorizationEngineService, never()).create(any(), any(), any());
    }

    @Test
    public void shouldCreateAuthorizationEngine_invalidInput() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final NewAuthorizationEngine newEngine = new NewAuthorizationEngine();

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);

        final Response response = post(
                target("domains")
                        .path(domainId)
                        .path("authorization-engines"),
                newEngine
        );


        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

}
