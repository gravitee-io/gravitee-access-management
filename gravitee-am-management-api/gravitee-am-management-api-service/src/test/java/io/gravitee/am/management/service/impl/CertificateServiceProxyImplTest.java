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
package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Template;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.am.service.model.UpdateCertificate;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceProxyImplTest {
    public static final String TEST_DOMAIN = "testDomain";
    private final Random random = new Random(1337);
    private final RandomStringGenerator randomIdGen = new RandomStringGenerator.Builder()
            .usingRandom(random::nextInt)
            .withinRange('a', 'a')
            .build();

    @Mock
    CertificateService certService;
    @Mock
    IdentityProviderService idpService;
    @Mock
    ApplicationService appService;
    @Mock
    CertificatePluginService certPluginService;
    @Mock
    AuditService auditService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    NotifierSettings settings = new NotifierSettings(true, Template.CERTIFICATE_EXPIRATION, "* * * * *", List.of(30), "subject");

    @Test
    void shouldFindCertsWithUsages() {
        when(certService.findByDomain(TEST_DOMAIN)).thenReturn(Flowable.fromIterable(List.of(systemCert(), validCert(), expiredCert())));
        when(certPluginService.getSchema(any())).thenReturn(Maybe.just("{\"content\":{}}"));
        when(appService.findByCertificate(any())).thenAnswer(i -> someRandomApps());
        when(idpService.findByCertificate(eq(Reference.domain(TEST_DOMAIN)), any())).thenAnswer(i -> someRandomIdps());

        var service = certificateServiceProxy();
        var foundCerts = service.findByDomainAndUse(TEST_DOMAIN, null)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors()
                .values()
                .get(0);
        assertThat(foundCerts)
                .hasSize(3)
                .anySatisfy(cert -> assertThat(cert.status()).isEqualTo(CertificateStatus.VALID))
                .anySatisfy(cert -> assertThat(cert.status()).isEqualTo(CertificateStatus.EXPIRED))
                .allSatisfy(cert -> assertThat(cert.applications()).isNotEmpty())
                .allSatisfy(cert -> assertThat(cert.identityProviders()).isNotEmpty())
        ;

    }

    @Test
    void shouldReturnStubIfSchemaIsMissing() {
        String missingSchema = "missing-schema";
        when(certService.findByDomain(TEST_DOMAIN)).thenReturn(Flowable.fromIterable(List.of(missingSchemaCert(missingSchema))));
        when(certPluginService.getSchema(missingSchema)).thenReturn(Maybe.empty());
        when(appService.findByCertificate(any())).thenAnswer(i -> someRandomApps());
        when(idpService.findByCertificate(eq(Reference.domain(TEST_DOMAIN)), any())).thenAnswer(i -> someRandomIdps());

        var service = certificateServiceProxy();

        var foundCerts = service.findByDomainAndUse(TEST_DOMAIN, null)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete()
                .assertNoErrors()
                .values()
                .get(0);
        assertThat(foundCerts)
                .hasSize(1)
                .anySatisfy(cert -> assertThat(cert.status()).isEqualTo(CertificateStatus.WILL_EXPIRE))
                .allSatisfy(cert -> assertThat(cert.applications()).isNotEmpty())
                .allSatisfy(cert -> assertThat(cert.identityProviders()).isNotEmpty())
        ;

    }

    @Test
    void shouldAuditWhenCreateFailsBeforeFiltering() {
        var domain = new Domain();
        domain.setId(TEST_DOMAIN);
        var request = new NewCertificate();
        request.setName("my-cert");
        request.setType("cert");
        request.setConfiguration("{}");
        User principal = mock(User.class);
        when(certService.create(domain, request, principal, false)).thenReturn(Single.error(new TechnicalManagementException("boom")));

        certificateServiceProxy()
                .create(domain, request, principal)
                .test()
                .assertError(TechnicalManagementException.class);

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo(EventType.CERTIFICATE_CREATED);
        assertThat(audit.getReferenceId()).isEqualTo(TEST_DOMAIN);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
    }

    @Test
    void shouldAuditWhenUpdateFailsBeforeFiltering() {
        var domain = new Domain();
        domain.setId(TEST_DOMAIN);
        var principal = mock(User.class);
        var update = new UpdateCertificate();
        update.setName("updated");
        update.setType("cert");
        update.setConfiguration("{\"key\":\"value\"}");

        var existing = minimalCert();
        existing.setId("cert-id");
        existing.setType("cert");
        existing.setConfiguration("{\"key\":\"value\"}");
        when(certService.findById("cert-id")).thenReturn(Maybe.just(existing));
        when(certPluginService.getSchema(anyString())).thenReturn(Maybe.just("{\"type\":\"object\"}"));
        when(certService.update(domain, "cert-id", update, principal)).thenReturn(Single.error(new TechnicalManagementException("validation error")));

        certificateServiceProxy()
                .update(domain, "cert-id", update, principal)
                .test()
                .assertError(TechnicalManagementException.class);

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo(EventType.CERTIFICATE_UPDATED);
        assertThat(audit.getReferenceId()).isEqualTo(TEST_DOMAIN);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
    }

    private CertificateServiceProxyImpl certificateServiceProxy() {
        return new CertificateServiceProxyImpl(certService, idpService, appService, certPluginService, auditService, objectMapper, settings);
    }

    private Audit captureAudit() {
        ArgumentCaptor<AuditBuilder<?>> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(captor.capture());
        return captor.getValue().build(objectMapper);
    }

    private Flowable<IdentityProvider> someRandomIdps() {
        var numIdps = random.nextInt(1, 5);
        return Flowable.fromStream(IntStream.range(0, numIdps).mapToObj(n -> {
            var idp = new IdentityProvider();
            idp.setId(randomIdGen.generate(5));
            idp.setName("idp-" + randomIdGen.generate(10));
            return idp;
        }));
    }

    private Flowable<Application> someRandomApps() {
        var numApps = random.nextInt(1, 5);
        return Flowable.fromStream(IntStream.range(0, numApps).mapToObj(n -> {
            var app = new Application();
            app.setId(randomIdGen.generate(5));
            app.setName("app-" + randomIdGen.generate(10));
            return app;
        }));
    }

    private Certificate validCert() {
        var cert = minimalCert();
        cert.setExpiresAt(Date.from(Instant.now().plus(60, ChronoUnit.DAYS)));
        return cert;
    }

    private Certificate minimalCert() {
        var cert = new Certificate();
        cert.setDomain(TEST_DOMAIN);
        cert.setName("cert-" + randomIdGen.generate(10));
        cert.setConfiguration("{\"name\": \"value\"}");
        return cert;
    }

    private Certificate systemCert() {
        var cert = minimalCert();
        cert.setSystem(true);
        cert.setExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        return cert;
    }

    private Certificate expiredCert() {
        var cert = minimalCert();
        cert.setExpiresAt(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)));
        return cert;
    }

    private Certificate missingSchemaCert(String schema) {
        var cert = minimalCert();
        cert.setExpiresAt(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        cert.setType(schema);
        return cert;
    }
}
