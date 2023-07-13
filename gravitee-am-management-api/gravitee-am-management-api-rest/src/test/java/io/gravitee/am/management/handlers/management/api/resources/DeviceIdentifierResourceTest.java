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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Test;

import jakarta.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifierResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetDeviceIdentifier() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String deviceIdentifierId = "deviceIdentifier-id";
        final DeviceIdentifier mockDeviceIdentifier = new DeviceIdentifier();
        mockDeviceIdentifier.setId(deviceIdentifierId);
        mockDeviceIdentifier.setName("deviceIdentifier-name");
        mockDeviceIdentifier.setReferenceType(ReferenceType.DOMAIN);
        mockDeviceIdentifier.setReferenceId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockDeviceIdentifier)).when(deviceIdentifierService).findById(deviceIdentifierId);

        final Response response = target("domains").path(domainId).path("device-identifiers").path(deviceIdentifierId).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final DeviceIdentifier deviceIdentifier = readEntity(response, DeviceIdentifier.class);
        assertEquals(domainId, deviceIdentifier.getReferenceId());
        assertEquals(deviceIdentifierId, deviceIdentifier.getId());
    }

    @Test
    public void shouldGetDeviceIdentifier_notFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String deviceIdentifierId = "deviceIdentifier-id";

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(deviceIdentifierService).findById(deviceIdentifierId);

        final Response response = target("domains").path(domainId).path("device-identifiers").path(deviceIdentifierId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetDeviceIdentifier_domainNotFound() {
        final String domainId = "domain-id";
        final String deviceIdentifierId = "deviceIdentifier-id";

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).path("device-identifiers").path(deviceIdentifierId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetDeviceIdentifier_wrongDomain() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String deviceIdentifierId = "deviceIdentifier-id2";
        final DeviceIdentifier mockDeviceIdentifier = new DeviceIdentifier();
        mockDeviceIdentifier.setId(deviceIdentifierId);
        mockDeviceIdentifier.setName("deviceIdentifier-name");
        mockDeviceIdentifier.setReferenceType(ReferenceType.DOMAIN);
        mockDeviceIdentifier.setReferenceId("wrong-domain");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockDeviceIdentifier)).when(deviceIdentifierService).findById(deviceIdentifierId);

        final Response response = target("domains").path(domainId).path("device-identifiers").path(deviceIdentifierId).request().get();
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }
}
