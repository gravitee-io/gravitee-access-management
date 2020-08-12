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
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProvidersResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetIdentityProviders() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final IdentityProvider mockIdentityProvider = new IdentityProvider();
        mockIdentityProvider.setId("identityProvider-1-id");
        mockIdentityProvider.setName("identityProvider-1-name");
        mockIdentityProvider.setDomain(domainId);

        final IdentityProvider mockIdentityProvider2 = new IdentityProvider();
        mockIdentityProvider2.setId("identityProvider-2-id");
        mockIdentityProvider2.setName("identityProvider-2-name");
        mockIdentityProvider2.setDomain(domainId);

        final List<IdentityProvider> identityProviders = Arrays.asList(mockIdentityProvider, mockIdentityProvider2);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(identityProviders)).when(identityProviderService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("identities").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<IdentityProvider> responseEntity = response.readEntity(List.class);
        assertTrue(responseEntity.size() == 2);
    }

    @Test
    public void shouldGetIdentityProviders_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Single.error(new TechnicalManagementException("error occurs"))).when(identityProviderService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("identities").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldCreate() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewIdentityProvider newIdentityProvider = new NewIdentityProvider();
        newIdentityProvider.setName("extensionGrant-name");
        newIdentityProvider.setType("extensionGrant-type");
        newIdentityProvider.setConfiguration("extensionGrant-configuration");

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("identityProvider-id");
        identityProvider.setName("identityProvider-name");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(identityProvider)).when(identityProviderService).create(eq(domainId), any(), any());

        final Response response = target("domains")
                .path(domainId)
                .path("identities")
                .request().post(Entity.json(newIdentityProvider));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }
}
