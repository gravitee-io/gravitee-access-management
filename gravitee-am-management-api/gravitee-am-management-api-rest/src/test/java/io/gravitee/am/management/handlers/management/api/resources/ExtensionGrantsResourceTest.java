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
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.service.exception.PluginNotDeployedException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewExtensionGrant;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantsResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetExtensionGrants() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final ExtensionGrant mockExtensionGrant = new ExtensionGrant();
        mockExtensionGrant.setId("extensionGrant-1-id");
        mockExtensionGrant.setName("extensionGrant-1-name");
        mockExtensionGrant.setDomain(domainId);

        final ExtensionGrant mockExtensionGrant2 = new ExtensionGrant();
        mockExtensionGrant2.setId("extensionGrant-2-id");
        mockExtensionGrant2.setName("extensionGrant-2-name");
        mockExtensionGrant2.setDomain(domainId);


        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Flowable.just(mockExtensionGrant, mockExtensionGrant2)).when(extensionGrantService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("extensionGrants").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<ExtensionGrant> responseEntity = readEntity(response, List.class);
        assertEquals(2, responseEntity.size());
    }

    @Test
    public void shouldGetExtensionGrants_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Flowable.error(new TechnicalManagementException("error occurs"))).when(extensionGrantService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("extensionGrants").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldCreate() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewExtensionGrant newExtensionGrant = new NewExtensionGrant();
        newExtensionGrant.setName("extensionGrant-name");
        newExtensionGrant.setType("extensionGrant-type");
        newExtensionGrant.setConfiguration("{}");
        newExtensionGrant.setGrantType("extensionGrant:grantType");

        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setId("extensionGrant-id");
        extensionGrant.setName("extensionGrant-name");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(extensionGrant)).when(extensionGrantService).create(any(Domain.class), any(), any());
        doReturn(Completable.complete()).when(extensionGrantPluginService).checkPluginDeployment(any());

        final Response response = target("domains")
                .path(domainId)
                .path("extensionGrants")
                .request().post(Entity.json(newExtensionGrant));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }

    @Test
    public void shouldNotCreate_pluginNotDeployed() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewExtensionGrant newExtensionGrant = new NewExtensionGrant();
        newExtensionGrant.setName("extensionGrant-name");
        newExtensionGrant.setType("extensionGrant-type");
        newExtensionGrant.setConfiguration("extensionGrant-configuration");
        newExtensionGrant.setGrantType("extensionGrant:grantType");

        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setId("extensionGrant-id");
        extensionGrant.setName("extensionGrant-name");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.error(PluginNotDeployedException.forType(newExtensionGrant.getType()))).when(extensionGrantPluginService).checkPluginDeployment(any());

        final Response response = target("domains")
                .path(domainId)
                .path("extensionGrants")
                .request().post(Entity.json(newExtensionGrant));
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }
}
