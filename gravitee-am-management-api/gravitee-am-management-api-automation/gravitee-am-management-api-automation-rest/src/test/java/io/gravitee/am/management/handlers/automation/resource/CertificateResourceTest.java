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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.management.handlers.automation.AutomationJerseySpringTest;
import io.gravitee.am.management.handlers.automation.model.AutomationCertificate;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class CertificateResourceTest extends AutomationJerseySpringTest {

    private static final String DOMAIN_KEY = "customer-auth";
    private static final String CERT_KEY = "saml-signing";
    private final String domainId = AutomationIds.domainId(ENV_ID, DOMAIN_KEY);
    private final String certId = AutomationIds.certificateId(domainId, CERT_KEY);

    private Domain domain() {
        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAutomationKey(DOMAIN_KEY);
        domain.setReferenceId(ENV_ID);
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        return domain;
    }

    private Certificate cert(boolean system, ManagedBy managedBy) {
        Certificate c = new Certificate();
        c.setId(certId);
        c.setAutomationKey(CERT_KEY);
        c.setName("SAML signing");
        c.setType("javakeystore-am-certificate");
        c.setConfiguration("{}");
        c.setSystem(system);
        c.setDomain(domainId);
        c.setManagedBy(managedBy);
        return c;
    }

    private Response getRequest() {
        return certificatesTarget(DOMAIN_KEY).path(CERT_KEY).request().get();
    }

    @Test
    void get_returns_certificate_when_found() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(anyString())).thenReturn(Flowable.just(cert(true, ManagedBy.AUTOMATION_API)));

        Response response = getRequest();

        assertEquals(200, response.getStatus());
        AutomationCertificate body = readEntity(response, AutomationCertificate.class);
        assertEquals(CERT_KEY, body.getAutomationKey());
        assertTrue(body.isSystem(), "system certificate must be projected as system:true");
    }

    @Test
    void get_returns_404_when_certificate_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(anyString())).thenReturn(Flowable.empty());

        assertEquals(404, getRequest().getStatus());
    }

    @Test
    void get_returns_404_when_certificate_not_automation_managed() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(anyString())).thenReturn(Flowable.just(cert(false, null)));

        assertEquals(404, getRequest().getStatus());
    }

    @Test
    void get_returns_404_when_domain_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        assertEquals(404, getRequest().getStatus());
    }

    @Test
    void delete_returns_204() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(anyString())).thenReturn(Flowable.just(cert(true, ManagedBy.AUTOMATION_API)));
        when(certificateService.delete(eq(certId), any())).thenReturn(Completable.complete());

        Response response = certificatesTarget(DOMAIN_KEY).path(CERT_KEY).request().delete();

        assertEquals(204, response.getStatus());
    }

    @Test
    void delete_returns_204_when_certificate_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(anyString())).thenReturn(Flowable.empty());

        Response response = certificatesTarget(DOMAIN_KEY).path(CERT_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(certificateService, never()).delete(anyString(), any());
    }

    @Test
    void delete_returns_204_when_domain_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        Response response = certificatesTarget(DOMAIN_KEY).path(CERT_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(certificateService, never()).delete(anyString(), any());
    }

    @Test
    void get_returns_403_without_revealing_existence_when_not_permitted() {
        denyPermission();
        // domain stubbed absent: an unauthorized caller must still get 403, never a 404 that leaks existence.
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        assertEquals(403, getRequest().getStatus());
    }

    @Test
    void delete_returns_403_when_not_permitted() {
        denyPermission();

        Response response = certificatesTarget(DOMAIN_KEY).path(CERT_KEY).request().delete();

        assertEquals(403, response.getStatus());
        verify(certificateService, never()).delete(anyString(), any());
    }
}
