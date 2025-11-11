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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.handlers.management.api.utils.AuditTestUtils;
import io.gravitee.am.management.handlers.management.api.utils.CertificateCredentialTestFixtures;
import io.gravitee.am.management.handlers.management.api.utils.DomainTestFixtures;
import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.CertificateCredentialAuditBuilder;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author GraviteeSource Team
 */
class UserCertCredentialResourceTest extends JerseySpringTest {

    // Test constants
    private static final String DOMAIN_ID = "domain-1";
    private static final String USER_ID = "user-1";
    private static final String CREDENTIAL_ID = "credential-1";
    
    @BeforeEach
    void setUp() {
        clearInvocations(auditService, domainService, certificateCredentialService);
    }

    @Test
    void shouldGetCertificateCredential() {
        final Domain mockDomain = DomainTestFixtures.createDomain(DOMAIN_ID);
        final CertificateCredential mockCredential = CertificateCredentialTestFixtures.buildMinimalCertificateCredential(
                DOMAIN_ID, USER_ID);
        mockCredential.setId(CREDENTIAL_ID);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(mockCredential)).when(certificateCredentialService).findById(any(Domain.class), eq(CREDENTIAL_ID));

        final Response response = buildCertCredentialPath().request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    void shouldGetCertificateCredential_domainNotFound() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = buildCertCredentialPath().request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    void shouldGetCertificateCredential_credentialNotFound() {
        final Domain mockDomain = DomainTestFixtures.createDomain(DOMAIN_ID);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(certificateCredentialService).findById(any(Domain.class), eq(CREDENTIAL_ID));

        final Response response = buildCertCredentialPath().request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    void shouldGetCertificateCredential_technicalManagementException() {
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(domainService).findById(DOMAIN_ID);

        final Response response = buildCertCredentialPath().request().get();

        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    void shouldDeleteCertificateCredential() {
        final Domain mockDomain = DomainTestFixtures.createDomain(DOMAIN_ID);
        final CertificateCredential mockCredential = CertificateCredentialTestFixtures.buildMinimalCertificateCredential(
                DOMAIN_ID, USER_ID);
        mockCredential.setId(CREDENTIAL_ID);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(mockCredential)).when(certificateCredentialService)
                .deleteByDomainAndUserAndId(any(Domain.class), eq(USER_ID), eq(CREDENTIAL_ID));

        final Response response = buildCertCredentialPath().request().delete();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
        verify(certificateCredentialService, times(1))
                .deleteByDomainAndUserAndId(any(Domain.class), eq(USER_ID), eq(CREDENTIAL_ID));
        verify(auditService, times(1)).report(AuditTestUtils.auditBuilderMatcher(EventType.CREDENTIAL_DELETED));
    }

    @Test
    void shouldDeleteCertificateCredential_domainNotFound() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = buildCertCredentialPath().request().delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    void shouldDeleteCertificateCredential_credentialNotFound() {
        final Domain mockDomain = DomainTestFixtures.createDomain(DOMAIN_ID);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(certificateCredentialService)
                .deleteByDomainAndUserAndId(any(Domain.class), eq(USER_ID), eq(CREDENTIAL_ID));

        final Response response = buildCertCredentialPath().request().delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(certificateCredentialService, times(1))
                .deleteByDomainAndUserAndId(any(Domain.class), eq(USER_ID), eq(CREDENTIAL_ID));
        verify(auditService, never()).report(any(AuditBuilder.class));
    }

    @Test
    void shouldDeleteCertificateCredential_technicalManagementException() {
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(domainService).findById(DOMAIN_ID);

        final Response response = buildCertCredentialPath().request().delete();

        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
        verify(auditService, never()).report(any(AuditBuilder.class));
    }

    @Test
    void shouldDeleteCertificateCredential_verifyAuditLoggingOnError() {
        final Domain mockDomain = DomainTestFixtures.createDomain(DOMAIN_ID);
        final CertificateCredential mockCredential = CertificateCredentialTestFixtures.buildMinimalCertificateCredential(
                DOMAIN_ID, USER_ID);
        mockCredential.setId(CREDENTIAL_ID);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.error(new TechnicalManagementException("Delete failed")))
                .when(certificateCredentialService)
                .deleteByDomainAndUserAndId(any(Domain.class), eq(USER_ID), eq(CREDENTIAL_ID));

        final Response response = buildCertCredentialPath().request().delete();

        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
        verify(certificateCredentialService, times(1))
                .deleteByDomainAndUserAndId(any(Domain.class), eq(USER_ID), eq(CREDENTIAL_ID));
        verify(auditService, times(1)).report(argThat(builder -> {
            if (builder instanceof CertificateCredentialAuditBuilder) {
                String eventType = (String) ReflectionTestUtils.getField(builder, "type");
                return EventType.CREDENTIAL_DELETED.equals(eventType);
            }
            return false;
        }));
    }

    // Helper methods

    /**
     * Build the cert-credentials/{credentialId} endpoint path.
     */
    private jakarta.ws.rs.client.WebTarget buildCertCredentialPath() {
        return target("domains").path(DOMAIN_ID).path("users").path(USER_ID).path("cert-credentials").path(CREDENTIAL_ID);
    }
}

