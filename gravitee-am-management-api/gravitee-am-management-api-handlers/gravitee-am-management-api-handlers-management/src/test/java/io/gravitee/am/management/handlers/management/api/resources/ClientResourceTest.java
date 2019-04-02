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
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;

import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetClient() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";
        final Client mockClient = new Client();
        mockClient.setId(clientId);
        mockClient.setClientId("client-name");
        mockClient.setDomain(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockClient)).when(clientService).findById(clientId);

        final Response response = target("domains").path(domainId).path("clients").path(clientId).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Client client = response.readEntity(Client.class);
        assertEquals(domainId, client.getDomain());
        assertEquals(clientId, client.getId());
    }

    @Test
    public void shouldGetClient_notFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(clientService).findById(clientId);

        final Response response = target("domains").path(domainId).path("clients").path(clientId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }


    @Test
    public void shouldGetClient_domainNotFound() {
        final String domainId = "domain-id";
        final String clientId = "client-id";

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).path("clients").path(clientId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetClient_wrongDomain() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";
        final Client mockClient = new Client();
        mockClient.setId(clientId);
        mockClient.setClientId("client-name");
        mockClient.setDomain("wrong-domain");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockClient)).when(clientService).findById(clientId);

        final Response response = target("domains").path(domainId).path("clients").path(clientId).request().get();
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldRenewClientSecret() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";
        final Client mockClient = new Client();
        mockClient.setId(clientId);
        mockClient.setClientId("client-name");
        mockClient.setDomain(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(mockClient)).when(clientService).renewClientSecret(domainId, clientId, null);

        final Response response = target("domains")
                .path(domainId)
                .path("clients")
                .path(clientId)
                .path("secret/_renew")
                .request()
                .post(null);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldRenewClientSecret_clientNotFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";
        final Client mockClient = new Client();
        mockClient.setId(clientId);
        mockClient.setClientId("client-name");
        mockClient.setDomain(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.error(new ClientNotFoundException(clientId))).when(clientService).renewClientSecret(domainId, clientId, null);

        final Response response = target("domains")
                .path(domainId)
                .path("clients")
                .path(clientId)
                .path("secret/_renew")
                .request()
                .post(null);
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }
}
