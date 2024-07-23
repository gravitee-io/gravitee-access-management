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
import io.gravitee.am.management.service.impl.CertificateEntity;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;

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

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(List.of(mockCertificate, mockCertificate2))).when(certificateService).findByDomainAndUse(domainId, null);
        doReturn(Flowable.empty()).when(applicationService).findByCertificate(anyString());

        final Response response = target("domains").path(domainId).path("certificates").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<Certificate> responseEntity = readEntity(response, List.class);
        assertEquals(2, responseEntity.size());
    }

    @Test
    public void shouldGetCertificatesWithDefaultSignatureUsage() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Certificate defaultCertificate = new Certificate();
        defaultCertificate.setId("certificate-1-id");
        defaultCertificate.setName("certificate-1-name");
        defaultCertificate.setDomain(domainId);
        defaultCertificate.setConfiguration("{}");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(List.of(defaultCertificate))).when(certificateService).findByDomainAndUse(domainId, "sig");
        doReturn(Flowable.empty()).when(applicationService).findByCertificate(anyString());

        final Response response = target("domains").path(domainId).path("certificates").queryParam("use", "sig").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<Certificate> responseEntity = readEntity(response, List.class);
        assertEquals(1, responseEntity.size());
    }

    @Test
    public void shouldGetCertificates_with_usages() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Certificate mockCertificate = new Certificate();
        mockCertificate.setId("certificate-1-id");
        mockCertificate.setName("certificate-1-name");
        mockCertificate.setDomain(domainId);
        mockCertificate.setConfiguration("{\"jks\":\"keystore.jks\",\"storepass\":\"password\",\"alias\":\"abc\",\"keypass\":\"password\",\"use\":[\"sig\", \"enc\"]}");

        final Certificate mockCertificate2 = new Certificate();
        mockCertificate2.setId("certificate-2-id");
        mockCertificate2.setName("certificate-2-name");
        mockCertificate2.setDomain(domainId);
        mockCertificate2.setConfiguration("{\"jks\":\"keystore.jks\",\"storepass\":\"password\",\"alias\":\"abc\",\"keypass\":\"password\",\"use\":[\"mtls\"]}");

        final Certificate mockCertificate3 = new Certificate();
        mockCertificate3.setId("certificate-3-id");
        mockCertificate3.setName("certificate-3-name");
        mockCertificate3.setDomain(domainId);
        mockCertificate3.setConfiguration("{\"jks\":\"keystore.jks\",\"storepass\":\"password\",\"alias\":\"abc\",\"keypass\":\"password\"}");

        final Certificate mockCertificate4 = new Certificate();
        mockCertificate4.setId("certificate-4-id");
        mockCertificate4.setName("certificate-4-name");
        mockCertificate4.setDomain(domainId);
        mockCertificate4.setConfiguration(null);


        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(List.of(mockCertificate, mockCertificate2, mockCertificate3, mockCertificate4))).when(certificateService).findByDomainAndUse(domainId, null);
        doReturn(Flowable.empty()).when(applicationService).findByCertificate(anyString());

        final Response response = target("domains").path(domainId).path("certificates").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<CertificateEntity> responseEntity = readEntity(response, List.class);
        assertEquals(4, responseEntity.size());

        CertificateEntity responseCert1 = responseEntity.stream().filter(c -> c.id().equals("certificate-1-id"))
                .findFirst().orElseThrow();
        CertificateEntity responseCert2 = responseEntity.stream().filter(c -> c.id().equals("certificate-2-id"))
                .findFirst().orElseThrow();
        CertificateEntity responseCert3 = responseEntity.stream().filter(c -> c.id().equals("certificate-3-id"))
                .findFirst().orElseThrow();
        CertificateEntity responseCert4 = responseEntity.stream().filter(c -> c.id().equals("certificate-4-id"))
                .findFirst().orElseThrow();

        assertTrue(responseCert1.usage().containsAll(List.of("sig", "enc")));
        assertTrue(responseCert2.usage().contains("mtls"));
        assertTrue(responseCert3.usage().isEmpty());
        assertTrue(responseCert4.usage().isEmpty());

    }

    @Test
    public void shouldGetCertificates_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Single.error(new TechnicalManagementException("error occurs"))).when(certificateService).findByDomainAndUse(domainId, null);

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
        doReturn(Single.just(certificate)).when(certificateService).create(eq(domainId), any(), any());

        final Response response = target("domains")
                .path(domainId)
                .path("certificates")
                .request().post(Entity.json(newCertificate));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }

    @Test
    public void shouldRotate() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        Certificate certificate = new Certificate();
        certificate.setId("certificate-id");
        certificate.setName("certificate-name");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just("certificate-schema")).when(certificatePluginService).getSchema(anyString());
        doReturn(Single.just(certificate)).when(certificateService).rotate(eq(domainId), any());

        final Response response = target("domains")
                .path(domainId)
                .path("certificates")
                .path("rotate")
                .request().post(Entity.json(null));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        assertTrue(response.getHeaderString(HttpHeaders.LOCATION).endsWith("certificates/" + certificate.getId()));
    }
}
