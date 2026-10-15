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

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.model.UpdateCertificate;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetCertificate() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String certificateId = "certificate-id";
        final Certificate mockCertificate = new Certificate();
        mockCertificate.setId(certificateId);
        mockCertificate.setName("certificate-name");
        mockCertificate.setDomain(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockCertificate)).when(certificateService).findById(domainId, certificateId);

        final Response response = target("domains").path(domainId).path("certificates").path(certificateId).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Certificate certificate = readEntity(response, Certificate.class);
        assertEquals(domainId, certificate.getDomain());
        assertEquals(certificateId, certificate.getId());
    }

    @Test
    public void shouldGetCertificate_notFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String certificateId = "certificate-id";

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(certificateService).findById(domainId, certificateId);

        final Response response = target("domains").path(domainId).path("certificates").path(certificateId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }


    @Test
    public void shouldGetCertificate_domainNotFound() {
        final String domainId = "domain-id";
        final String certificateId = "certificate-id";

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).path("certificates").path(certificateId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetCertificate_wrongDomain() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String certificateId = "certificate-id";
        final Certificate mockCertificate = new Certificate();
        mockCertificate.setId(certificateId);
        mockCertificate.setName("certificate-name");
        mockCertificate.setDomain("wrong-domain");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(certificateService).findById(domainId, certificateId);

        final Response response = target("domains").path(domainId).path("certificates").path(certificateId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotUpdateCertificate_otherDomain() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);
        final String certificateId = "other-domain-certificate-id";

        final UpdateCertificate updateCertificate = new UpdateCertificate();
        updateCertificate.setName("certificate-name");
        updateCertificate.setType("pkcs12-am-certificate");
        updateCertificate.setConfiguration("{}");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.error(new CertificateNotFoundException(certificateId)))
                .when(certificateService).update(eq(mockDomain), eq(certificateId), any(), any());

        final Response response = put(target("domains").path(domainId).path("certificates").path(certificateId), updateCertificate);
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotDeleteCertificate_otherDomain() {
        final String domainId = "domain-id";
        final String certificateId = "other-domain-certificate-id";

        doReturn(Completable.error(new CertificateNotFoundException(certificateId)))
                .when(certificateService).delete(eq(domainId), eq(certificateId), any());

        final Response response = target("domains").path(domainId).path("certificates").path(certificateId).request().delete();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotGetCertificatePublicKey_otherDomain() {
        final String domainId = "domain-id";
        final String certificateId = "other-domain-certificate-id";

        final CertificateProvider provider = mock(CertificateProvider.class);
        doReturn(Maybe.just(provider)).when(certificateManager).getCertificateProvider(certificateId);
        doReturn(Maybe.empty()).when(certificateService).findById(domainId, certificateId);

        final Response response = target("domains").path(domainId).path("certificates").path(certificateId).path("key").request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(provider, never()).publicKey();
    }

    @Test
    public void shouldNotGetCertificatePublicKeys_otherDomain() {
        final String domainId = "domain-id";
        final String certificateId = "other-domain-certificate-id";

        final CertificateProvider provider = mock(CertificateProvider.class);
        doReturn(Maybe.just(provider)).when(certificateManager).getCertificateProvider(certificateId);
        doReturn(Maybe.empty()).when(certificateService).findById(domainId, certificateId);

        final Response response = target("domains").path(domainId).path("certificates").path(certificateId).path("keys").request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(provider, never()).publicKeys();
    }
}
