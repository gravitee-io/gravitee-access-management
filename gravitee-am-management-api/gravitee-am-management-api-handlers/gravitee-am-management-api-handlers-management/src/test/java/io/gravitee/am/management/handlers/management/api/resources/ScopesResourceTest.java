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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewScope;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ScopesResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetScopes() {
        final String domainId = "domain-1";
        final Scope mockScope = new Scope();
        mockScope.setId("scope-1-id");
        mockScope.setKey("scope-1-id");
        mockScope.setName("scope-1-name");
        mockScope.setDomain(domainId);

        final Scope mockScope2 = new Scope();
        mockScope2.setId("scope-2-id");
        mockScope2.setKey("scope-2-id");
        mockScope2.setName("scope-2-name");
        mockScope2.setDomain(domainId);

        final Set<Scope> scopes = new HashSet<>(Arrays.asList(mockScope, mockScope2));

        doReturn(Single.just(scopes)).when(scopeService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("scopes").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<Scope> responseEntity = response.readEntity(List.class);
        assertTrue(responseEntity.size() == 2);
    }

    @Test
    public void shouldGetScopes_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Single.error(new TechnicalManagementException("error occurs"))).when(scopeService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("scopes").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }


    @Test
    public void shouldCreate() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewScope newScope = new NewScope();
        newScope.setKey("scope-key");
        newScope.setName("scope-name");
        newScope.setDescription("scope-description");

        Scope scope = new Scope();
        scope.setId("scope-id");
        scope.setKey("scope-key");
        scope.setName("scope-name");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(scope)).when(scopeService).create(eq(domainId), any(NewScope.class), any());

        final Response response = target("domains")
                .path(domainId)
                .path("scopes")
                .request().post(Entity.json(newScope));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }
}
