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
import io.gravitee.am.management.handlers.management.api.schemas.NewCertificateCredential;
import io.gravitee.am.management.handlers.management.api.utils.AuditTestUtils;
import io.gravitee.am.management.handlers.management.api.utils.CertificateCredentialTestFixtures;
import io.gravitee.am.management.handlers.management.api.utils.CertificateTestUtils;
import io.gravitee.am.management.handlers.management.api.utils.DomainTestFixtures;
import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.exception.CertificateExpiredException;
import io.gravitee.am.service.exception.CertificateLimitExceededException;
import io.gravitee.am.service.exception.DuplicateCertificateException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author GraviteeSource Team
 */
class UserCertCredentialsResourceTest extends JerseySpringTest {

    // Test constants
    private static final String DOMAIN_ID = "domain-1";
    private static final String USER_ID = "user-1";
    private static final String DEVICE_NAME = "My Laptop";
    private static final String VALID_PEM_CERT = CertificateTestUtils.generateValidCertificatePEM();
    
    @BeforeEach
    void setUp() {
        clearInvocations(auditService, domainService, certificateCredentialService);
    }

    @Test
    void shouldListUserCertificateCredentials() {
        final Domain mockDomain = DomainTestFixtures.createDomain(DOMAIN_ID);
        final CertificateCredential mockCredential = CertificateCredentialTestFixtures.buildMinimalCertificateCredential(
                DOMAIN_ID, USER_ID);
        mockCredential.setId("credential-id");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Flowable.just(mockCredential)).when(certificateCredentialService).findByUserId(any(Domain.class), eq(USER_ID));

        final Response response = buildCertCredentialsPath().request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    void shouldListUserCertificateCredentials_domainNotFound() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = buildCertCredentialsPath().request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    void shouldListUserCertificateCredentials_technicalManagementException() {
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(domainService).findById(DOMAIN_ID);

        final Response response = buildCertCredentialsPath().request().get();

        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    void shouldEnrollCertificateCredential() {
        final Domain mockDomain = DomainTestFixtures.createDomain(DOMAIN_ID);
        final CertificateCredential mockCredential = CertificateCredentialTestFixtures.buildMinimalCertificateCredential(
                DOMAIN_ID, USER_ID);
        mockCredential.setId("credential-id");

        final NewCertificateCredential newCredential = createNewCertificateCredential(VALID_PEM_CERT, DEVICE_NAME);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(mockCredential)).when(certificateCredentialService).enrollCertificate(
                any(Domain.class), eq(USER_ID), eq(VALID_PEM_CERT), eq(DEVICE_NAME));

        final Response response = buildCertCredentialsPath().request()
                .post(Entity.entity(newCredential, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        assertNotNull(response.getLocation());
        verify(auditService, times(1)).report(AuditTestUtils.auditBuilderMatcher(EventType.CREDENTIAL_CREATED));
    }

    @Test
    void shouldEnrollCertificateCredential_domainNotFound() {
        final NewCertificateCredential newCredential = createNewCertificateCredential(VALID_PEM_CERT, null);

        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = buildCertCredentialsPath().request()
                .post(Entity.entity(newCredential, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(auditService, never()).report(any(AuditBuilder.class));
    }

    @Test
    void shouldEnrollCertificateCredential_expiredCertificate() {
        final Domain mockDomain = DomainTestFixtures.createDomain(DOMAIN_ID);
        final NewCertificateCredential newCredential = createNewCertificateCredential(VALID_PEM_CERT, null);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.error(new CertificateExpiredException("Certificate has expired")))
                .when(certificateCredentialService).enrollCertificate(any(Domain.class), eq(USER_ID), anyString(), any());

        final Response response = buildCertCredentialsPath().request()
                .post(Entity.entity(newCredential, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        verify(auditService, times(1)).report(AuditTestUtils.auditBuilderMatcher(EventType.CREDENTIAL_CREATED));
    }

    @Test
    void shouldEnrollCertificateCredential_duplicateCertificate() {
        final Domain mockDomain = DomainTestFixtures.createDomain(DOMAIN_ID);
        final NewCertificateCredential newCredential = createNewCertificateCredential(VALID_PEM_CERT, null);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.error(new DuplicateCertificateException("Certificate with this thumbprint already exists")))
                .when(certificateCredentialService).enrollCertificate(any(Domain.class), eq(USER_ID), anyString(), any());

        final Response response = buildCertCredentialsPath().request()
                .post(Entity.entity(newCredential, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(HttpStatusCode.CONFLICT_409, response.getStatus());
    }

    @Test
    void shouldEnrollCertificateCredential_limitExceeded() {
        final Domain mockDomain = DomainTestFixtures.createDomain(DOMAIN_ID);
        final NewCertificateCredential newCredential = createNewCertificateCredential(VALID_PEM_CERT, null);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.error(new CertificateLimitExceededException("Maximum number of certificates exceeded")))
                .when(certificateCredentialService).enrollCertificate(any(Domain.class), eq(USER_ID), anyString(), any());

        final Response response = buildCertCredentialsPath().request()
                .post(Entity.entity(newCredential, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    void shouldEnrollCertificateCredential_badRequest_missingCertificate() {
        final NewCertificateCredential newCredential = new NewCertificateCredential();

        final Response response = buildCertCredentialsPath().request()
                .post(Entity.entity(newCredential, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        verify(auditService, never()).report(any(AuditBuilder.class));
    }

    @Test
    void shouldEnrollCertificateCredential_technicalManagementException() {
        final Domain mockDomain = DomainTestFixtures.createDomain(DOMAIN_ID);
        final NewCertificateCredential newCredential = createNewCertificateCredential(VALID_PEM_CERT, null);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.error(new TechnicalManagementException("Technical error occurred")))
                .when(certificateCredentialService).enrollCertificate(any(Domain.class), eq(USER_ID), anyString(), any());

        final Response response = buildCertCredentialsPath().request()
                .post(Entity.entity(newCredential, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
        verify(auditService, times(1)).report(AuditTestUtils.auditBuilderMatcher(EventType.CREDENTIAL_CREATED));
    }

    // Helper methods

    /**
     * Build the cert-credentials endpoint path.
     */
    private jakarta.ws.rs.client.WebTarget buildCertCredentialsPath() {
        return target("domains").path(DOMAIN_ID).path("users").path(USER_ID).path("cert-credentials");
    }

    /**
     * Create a NewCertificateCredential for testing.
     */
    private NewCertificateCredential createNewCertificateCredential(String certificatePem, String deviceName) {
        final NewCertificateCredential credential = new NewCertificateCredential();
        credential.setCertificatePem(certificatePem);
        credential.setDeviceName(deviceName);
        return credential;
    }
}

