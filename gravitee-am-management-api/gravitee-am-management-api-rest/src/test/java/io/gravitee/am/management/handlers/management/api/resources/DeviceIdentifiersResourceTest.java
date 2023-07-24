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
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.exception.PluginNotDeployedException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewDeviceIdentifier;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifiersResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetdeviceIdentifiers() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        var mockDeviceIdentifier = new DeviceIdentifier();
        mockDeviceIdentifier.setId("deviceIdentifier-1-id");
        mockDeviceIdentifier.setName("deviceIdentifier-1-name");
        mockDeviceIdentifier.setType("Some version");
        mockDeviceIdentifier.setConfiguration("{\"config\" : \"Some value\"}}");
        mockDeviceIdentifier.setReferenceType(ReferenceType.DOMAIN);
        mockDeviceIdentifier.setReferenceId(domainId);

        final DeviceIdentifier mockdeviceIdentifier2 = new DeviceIdentifier();
        mockdeviceIdentifier2.setId("deviceIdentifier-2-id");
        mockdeviceIdentifier2.setName("deviceIdentifier-2-name");
        mockdeviceIdentifier2.setType("Some other version");
        mockdeviceIdentifier2.setConfiguration("{\"config\" : \"Some value\"}");
        mockdeviceIdentifier2.setReferenceType(ReferenceType.DOMAIN);
        mockdeviceIdentifier2.setReferenceId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Flowable.just(mockDeviceIdentifier, mockdeviceIdentifier2)).when(deviceIdentifierService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("device-identifiers").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<DeviceIdentifier> responseEntity = readEntity(response, List.class);
        assertTrue(responseEntity.size() == 2);
    }

    @Test
    public void shouldGetdeviceIdentifiers_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Flowable.error(new TechnicalManagementException("error occurs"))).when(deviceIdentifierService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("device-identifiers").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldCreate() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        var newDeviceIdentifier = new NewDeviceIdentifier();
        newDeviceIdentifier.setName("newDeviceIdentifier-name");
        newDeviceIdentifier.setType("newDeviceIdentifier-type");
        newDeviceIdentifier.setConfiguration("newDeviceIdentifier-configuration");

        DeviceIdentifier deviceIdentifier = new DeviceIdentifier();
        deviceIdentifier.setId("deviceIdentifier-id");
        deviceIdentifier.setName("deviceIdentifier-name");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(deviceIdentifier)).when(deviceIdentifierService).create(eq(domainId), any(), any());
        doReturn(Completable.complete()).when(deviceIdentifierPluginService).checkPluginDeployment(any());

        final Response response = target("domains")
                .path(domainId)
                .path("device-identifiers")
                .request().post(Entity.json(newDeviceIdentifier));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }

    @Test
    public void shouldNotCreate_PLuginNotDeployed() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        var newDeviceIdentifier = new NewDeviceIdentifier();
        newDeviceIdentifier.setName("newDeviceIdentifier-name");
        newDeviceIdentifier.setType("newDeviceIdentifier-type");
        newDeviceIdentifier.setConfiguration("newDeviceIdentifier-configuration");

        DeviceIdentifier deviceIdentifier = new DeviceIdentifier();
        deviceIdentifier.setId("deviceIdentifier-id");
        deviceIdentifier.setName("deviceIdentifier-name");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.error(PluginNotDeployedException.forType(newDeviceIdentifier.getType()))).when(deviceIdentifierPluginService).checkPluginDeployment(any());

        final Response response = target("domains")
                .path(domainId)
                .path("device-identifiers")
                .request().post(Entity.json(newDeviceIdentifier));
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }
}
