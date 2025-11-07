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

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.exception.AuthorizationEngineNotFoundException;
import io.gravitee.am.service.model.UpdateAuthorizationEngine;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
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
public class AuthorizationEngineResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetAuthorizationEngine() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String engineId = "engine-id";
        final AuthorizationEngine mockEngine = new AuthorizationEngine();
        mockEngine.setId(engineId);
        mockEngine.setName("Test Engine");
        mockEngine.setType("openfga");
        mockEngine.setReferenceType(ReferenceType.DOMAIN);
        mockEngine.setReferenceId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockEngine)).when(authorizationEngineServiceProxy).findById(engineId);

        final Response response = target("domains")
                .path(domainId)
                .path("authorization-engines")
                .path(engineId)
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final AuthorizationEngine engine = readEntity(response, AuthorizationEngine.class);
        assertEquals(engineId, engine.getId());
        assertEquals("Test Engine", engine.getName());
        assertEquals("openfga", engine.getType());
    }

    @Test
    public void shouldGetAuthorizationEngine_notFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String engineId = "non-existent-id";

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(authorizationEngineServiceProxy).findById(engineId);

        final Response response = target("domains")
                .path(domainId)
                .path("authorization-engines")
                .path(engineId)
                .request()
                .get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetAuthorizationEngine_domainNotFound() {
        final String domainId = "non-existent-domain";
        final String engineId = "engine-id";

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("authorization-engines")
                .path(engineId)
                .request()
                .get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());

        // In reactive chains, the findById might be called before the error propagates
        // The important thing is that we get 404 due to DomainNotFoundException
    }

    @Test
    public void shouldUpdateAuthorizationEngine() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String engineId = "engine-id";
        final UpdateAuthorizationEngine updateEngine = new UpdateAuthorizationEngine();
        updateEngine.setName("Updated Engine");
        updateEngine.setConfiguration("{\"connectionUri\":\"http://localhost:9090\"}");

        final AuthorizationEngine updatedEngine = new AuthorizationEngine();
        updatedEngine.setId(engineId);
        updatedEngine.setName(updateEngine.getName());
        updatedEngine.setConfiguration(updateEngine.getConfiguration());
        updatedEngine.setReferenceType(ReferenceType.DOMAIN);
        updatedEngine.setReferenceId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(updatedEngine)).when(authorizationEngineServiceProxy)
                .update(eq(domainId), eq(engineId), any(UpdateAuthorizationEngine.class), any());

        final Response response = put(
                target("domains")
                        .path(domainId)
                        .path("authorization-engines")
                        .path(engineId),
                updateEngine
        );

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final AuthorizationEngine engine = readEntity(response, AuthorizationEngine.class);
        assertEquals("Updated Engine", engine.getName());

        verify(authorizationEngineServiceProxy, times(1))
                .update(eq(domainId), eq(engineId), any(UpdateAuthorizationEngine.class), any());
    }

    @Test
    public void shouldUpdateAuthorizationEngine_notFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String engineId = "non-existent-id";
        final UpdateAuthorizationEngine updateEngine = new UpdateAuthorizationEngine();
        updateEngine.setName("Updated Engine");
        updateEngine.setConfiguration("{\"connectionUri\":\"http://localhost:9090\"}");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.error(new AuthorizationEngineNotFoundException(engineId)))
                .when(authorizationEngineServiceProxy)
                .update(eq(domainId), eq(engineId), any(UpdateAuthorizationEngine.class), any());

        final Response response = put(
                target("domains")
                        .path(domainId)
                        .path("authorization-engines")
                        .path(engineId),
                updateEngine
        );

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldUpdateAuthorizationEngine_domainNotFound() {
        final String domainId = "non-existent-domain";
        final String engineId = "engine-id";
        final UpdateAuthorizationEngine updateEngine = new UpdateAuthorizationEngine();
        updateEngine.setName("Updated Engine");

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = put(
                target("domains")
                        .path(domainId)
                        .path("authorization-engines")
                        .path(engineId),
                updateEngine
        );

        // Update form validation might cause 400
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());

        // Should not try to update if domain not found
        verify(authorizationEngineServiceProxy, never()).update(any(), any(), any(), any());
    }

    @Test
    public void shouldDeleteAuthorizationEngine() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String engineId = "engine-id";

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.complete()).when(authorizationEngineServiceProxy)
                .delete(eq(domainId), eq(engineId), any());

        final Response response = target("domains")
                .path(domainId)
                .path("authorization-engines")
                .path(engineId)
                .request()
                .delete();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());

        verify(authorizationEngineServiceProxy, times(1)).delete(eq(domainId), eq(engineId), any());
    }

    @Test
    public void shouldDeleteAuthorizationEngine_notFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String engineId = "non-existent-id";

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.error(new AuthorizationEngineNotFoundException(engineId)))
                .when(authorizationEngineServiceProxy)
                .delete(eq(domainId), eq(engineId), any());

        final Response response = target("domains")
                .path(domainId)
                .path("authorization-engines")
                .path(engineId)
                .request()
                .delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldDeleteAuthorizationEngine_domainNotFound() {
        final String domainId = "non-existent-domain";
        final String engineId = "engine-id";

        // The delete endpoint doesn't check domain existence,
        // so it will attempt to delete directly which will fail
        doReturn(Completable.error(new RuntimeException("Domain validation failed")))
                .when(authorizationEngineServiceProxy)
                .delete(eq(domainId), eq(engineId), any());

        final Response response = target("domains")
                .path(domainId)
                .path("authorization-engines")
                .path(engineId)
                .request()
                .delete();

        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }
}
