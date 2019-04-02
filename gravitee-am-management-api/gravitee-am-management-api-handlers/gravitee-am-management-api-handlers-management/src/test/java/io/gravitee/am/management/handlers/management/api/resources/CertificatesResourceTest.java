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
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewCertificate;
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
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificatesResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetCertificates() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Certificate mockCertificate = new Certificate();
        mockCertificate.setId("certificate-1-id");
        mockCertificate.setName("certificate-1-name");
        mockCertificate.setDomain(domainId);

        final Certificate mockCertificate2 = new Certificate();
        mockCertificate2.setId("certificate-2-id");
        mockCertificate2.setName("certificate-2-name");
        mockCertificate2.setDomain(domainId);

        final List<Certificate> certificates = Arrays.asList(mockCertificate, mockCertificate2);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(certificates)).when(certificateService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("certificates").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<Certificate> responseEntity = response.readEntity(List.class);
        assertTrue(responseEntity.size() == 2);
    }

    @Test
    public void shouldGetCertificates_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Single.error(new TechnicalManagementException("error occurs"))).when(certificateService).findByDomain(domainId);

        final Response response = target("domains").path(domainId).path("certificates").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldCreate() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewCertificate newCertificate = new NewCertificate();
        newCertificate.setName("certificate-name");
        newCertificate.setType("certificate-type");
        newCertificate.setConfiguration("certificate-configuration");

        Certificate certificate = new Certificate();
        certificate.setId("certificate-id");
        certificate.setName("certificate-name");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just("certificate-schema")).when(certificatePluginService).getSchema(anyString());
        doReturn(Single.just(certificate)).when(certificateService).create(eq(domainId), any(), anyString(), any());

        final Response response = target("domains")
                .path(domainId)
                .path("certificates")
                .request().post(Entity.json(newCertificate));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }
}
