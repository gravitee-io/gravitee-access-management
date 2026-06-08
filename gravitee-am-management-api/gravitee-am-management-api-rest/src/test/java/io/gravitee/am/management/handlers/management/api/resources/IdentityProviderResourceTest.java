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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetIdentityProvider() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String identityProviderId = "identityProvider-id";
        final IdentityProvider mockIdentityProvider = new IdentityProvider();
        mockIdentityProvider.setId(identityProviderId);
        mockIdentityProvider.setName("identityProvider-name");
        mockIdentityProvider.setReferenceType(ReferenceType.DOMAIN);
        mockIdentityProvider.setReferenceId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockIdentityProvider)).when(identityProviderService).findById(identityProviderId);

        final Response response = target("domains").path(domainId).path("identities").path(identityProviderId).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final IdentityProvider identityProvider = readEntity(response, IdentityProvider.class);
        assertEquals(domainId, identityProvider.getReferenceId());
        assertEquals(identityProviderId, identityProvider.getId());
    }

    @Test
    public void shouldGetIdentityProvider_notFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String identityProviderId = "identityProvider-id";

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(identityProviderService).findById(identityProviderId);

        final Response response = target("domains").path(domainId).path("identities").path(identityProviderId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }


    @Test
    public void shouldGetIdentityProvider_domainNotFound() {
        final String domainId = "domain-id";
        final String identityProviderId = "identityProvider-id";

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).path("identities").path(identityProviderId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotUpdate_nullConfiguration() {
        final String domainId = "domain-id";
        final String identityProviderId = "identityProvider-id";

        UpdateIdentityProvider updateIdentityProvider = new UpdateIdentityProvider();
        updateIdentityProvider.setName("idp-name");
        updateIdentityProvider.setType("idp-type");
        // configuration intentionally omitted (null)

        final Response response = target("domains").path(domainId).path("identities").path(identityProviderId)
                .request().put(Entity.json(updateIdentityProvider));
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        final String body = response.readEntity(String.class);
        assertTrue("expected the message to mention the configuration field: " + body, body.contains("configuration"));
        assertFalse("message interpolation must not leak: " + body, body.contains("HV000149"));
    }

    @Test
    public void shouldGetIdentityProvider_wrongDomain() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String identityProviderId = "identityProvider-id";
        final IdentityProvider mockIdentityProvider = new IdentityProvider();
        mockIdentityProvider.setId(identityProviderId);
        mockIdentityProvider.setName("identityProvider-name");
        mockIdentityProvider.setReferenceType(ReferenceType.DOMAIN);
        mockIdentityProvider.setReferenceId("wrong-domain");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockIdentityProvider)).when(identityProviderService).findById(identityProviderId);

        final Response response = target("domains").path(domainId).path("identities").path(identityProviderId).request().get();
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }
}
