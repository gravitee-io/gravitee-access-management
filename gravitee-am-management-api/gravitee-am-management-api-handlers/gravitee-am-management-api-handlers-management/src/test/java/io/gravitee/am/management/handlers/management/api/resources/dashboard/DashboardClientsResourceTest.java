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
package io.gravitee.am.management.handlers.management.api.resources.dashboard;

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.TopClient;
import io.gravitee.am.service.model.TotalClient;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DashboardClientsResourceTest extends JerseySpringTest {

    @Test
    public void shouldListClients() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Client mockClient = new Client();
        mockClient.setId("client-1-id");
        mockClient.setClientId("client-1-name");
        mockClient.setDomain(domainId);
        mockClient.setUpdatedAt(new Date());

        final Client mockClient2 = new Client();
        mockClient2.setId("client-2-id");
        mockClient2.setClientId("client-2-name");
        mockClient2.setDomain(domainId);
        mockClient2.setUpdatedAt(new Date());

        final Set<Client> clients = new HashSet<>(Arrays.asList(mockClient, mockClient2));
        final Page<Client> pagedClients = new Page<>(clients, 0, 2);

        doReturn(Single.just(pagedClients)).when(clientService).findAll(0, 10);
        doReturn(Single.just(new HashSet<>(Arrays.asList(mockDomain)))).when(domainService).findByIdIn(new HashSet(Arrays.asList(domainId)));

        final Response response = target("dashboard")
                .path("clients")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldListClientsByDomain() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Client mockClient = new Client();
        mockClient.setId("client-1-id");
        mockClient.setClientId("client-1-name");
        mockClient.setDomain(domainId);
        mockClient.setUpdatedAt(new Date());

        final Client mockClient2 = new Client();
        mockClient2.setId("client-2-id");
        mockClient2.setClientId("client-2-name");
        mockClient2.setDomain(domainId);
        mockClient2.setUpdatedAt(new Date());

        final Set<Client> clients = new HashSet<>(Arrays.asList(mockClient, mockClient2));
        final Page<Client> pagedClients = new Page<>(clients, 0, 2);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(pagedClients)).when(clientService).findByDomain(domainId,0, 10);

        final Response response = target("dashboard")
                .path("clients")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .queryParam("domainId", domainId)
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldListClients_technicalManagementException() {
        doReturn(Single.error(new TechnicalManagementException("Error occurs"))).when(clientService).findAll(0, 10);
        final Response response = target("dashboard")
                .path("clients")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .request()
                .get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldListTopClients() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Client mockClient = new Client();
        mockClient.setId("client-1-id");
        mockClient.setClientId("client-1-name");
        mockClient.setDomain(domainId);
        mockClient.setUpdatedAt(new Date());

        final Client mockClient2 = new Client();
        mockClient2.setId("client-2-id");
        mockClient2.setClientId("client-2-name");
        mockClient2.setDomain(domainId);
        mockClient2.setUpdatedAt(new Date());

        final TopClient mockTopClient = new TopClient();
        mockTopClient.setClient(mockClient);
        mockTopClient.setAccessTokens(2l);

        final TopClient mockTopClient2 = new TopClient();
        mockTopClient2.setClient(mockClient2);
        mockTopClient2.setAccessTokens(3l);

        final Set<TopClient> topClients = new HashSet<>(Arrays.asList(mockTopClient, mockTopClient2));

        doReturn(Single.just(topClients)).when(clientService).findTopClients();
        doReturn(Single.just(new HashSet<>(Arrays.asList(mockDomain)))).when(domainService).findByIdIn(new HashSet(Arrays.asList(domainId)));

        final Response response = target("dashboard")
                .path("clients")
                .path("top")
                .queryParam("size", 10)
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldListTopClientsByDomain() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Client mockClient = new Client();
        mockClient.setId("client-1-id");
        mockClient.setClientId("client-1-name");
        mockClient.setDomain(domainId);
        mockClient.setUpdatedAt(new Date());

        final Client mockClient2 = new Client();
        mockClient2.setId("client-2-id");
        mockClient2.setClientId("client-2-name");
        mockClient2.setDomain(domainId);
        mockClient2.setUpdatedAt(new Date());

        final TopClient mockTopClient = new TopClient();
        mockTopClient.setClient(mockClient);
        mockTopClient.setAccessTokens(2l);

        final TopClient mockTopClient2 = new TopClient();
        mockTopClient2.setClient(mockClient2);
        mockTopClient2.setAccessTokens(3l);

        final Set<TopClient> topClients = new HashSet<>(Arrays.asList(mockTopClient, mockTopClient2));

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(topClients)).when(clientService).findTopClientsByDomain(domainId);

        final Response response = target("dashboard")
                .path("clients")
                .path("top")
                .queryParam("size", 10)
                .queryParam("domainId", domainId)
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldListTopClients_technicalManagementException() {
        doReturn(Single.error(new TechnicalManagementException("Error occurs"))).when(clientService).findTopClients();
        final Response response = target("dashboard")
                .path("clients")
                .path("top")
                .queryParam("size", 10)
                .request()
                .get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldListTotalClients() {
        final TotalClient totalClient = new TotalClient();
        totalClient.setTotalClients(10l);

        doReturn(Single.just(totalClient)).when(clientService).findTotalClients();
        final Response response = target("dashboard")
                .path("clients")
                .path("total")
                .request()
                .get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final TotalClient responseEntity = response.readEntity(TotalClient.class);

        assertEquals(10l, responseEntity.getTotalClients());
    }

    @Test
    public void shouldListTotalClientsByDomain() {
        final String domainId = "domain-1";
        final TotalClient totalClient = new TotalClient();
        totalClient.setTotalClients(10l);

        doReturn(Single.just(totalClient)).when(clientService).findTotalClientsByDomain(domainId);
        final Response response = target("dashboard")
                .path("clients")
                .path("total")
                .queryParam("domainId", domainId)
                .request()
                .get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final TotalClient responseEntity = response.readEntity(TotalClient.class);

        assertEquals(10l, responseEntity.getTotalClients());
    }

    @Test
    public void shouldListTotalClients_technicalManagementException() {
        doReturn(Single.error(new TechnicalManagementException("Error occurs"))).when(clientService).findTotalClients();
        final Response response = target("dashboard")
                .path("clients")
                .path("total")
                .request()
                .get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }
}
