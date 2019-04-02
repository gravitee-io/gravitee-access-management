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
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewClient;
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
public class ClientsResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetClients() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Client mockClient = new Client();
        mockClient.setId("client-1-id");
        mockClient.setClientId("client-1-name");
        mockClient.setDomain(domainId);

        final Client mockClient2 = new Client();
        mockClient2.setId("client-2-id");
        mockClient2.setClientId("client-2-name");
        mockClient2.setDomain(domainId);

        final Set<Client> clients = new HashSet<>(Arrays.asList(mockClient, mockClient2));

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(clients)).when(clientService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("clients").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<Client> responseEntity = response.readEntity(List.class);
        assertTrue(responseEntity.size() == 2);
    }

    @Test
    public void shouldGetClients_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Single.error(new TechnicalManagementException("error occurs"))).when(clientService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("clients").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }


    @Test
    public void shouldCreate() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewClient newClient = new NewClient();
        newClient.setClientId("client-id");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setClientSecret("client-secret");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(client)).when(clientService).create(eq(domainId), any(NewClient.class), any());

        final Response response = target("domains")
                .path(domainId)
                .path("clients")
                .request().post(Entity.json(newClient));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }
}
