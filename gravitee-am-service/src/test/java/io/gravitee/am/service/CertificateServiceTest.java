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
package io.gravitee.am.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.plugins.certificate.core.schema.CertificateSchema;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.exception.CertificateWithApplicationsException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.CertificateServiceImpl;
import io.gravitee.am.service.tasks.AssignSystemCertificate;
import io.gravitee.am.service.tasks.AssignSystemCertificateDefinition;
import io.gravitee.am.service.tasks.TaskType;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.internal.util.io.IOUtil;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.gravitee.am.service.impl.CertificateServiceImpl.DEFAULT_CERTIFICATE_PLUGIN;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateServiceTest {

    public static final String DOMAIN_NAME = "my-domain";
    public static String certificateSchemaDefinition;
    public static String certificateConfiguration;
    public static String certificateConfigurationWithOptions;
    @InjectMocks
    private CertificateService certificateService = new CertificateServiceImpl();

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private EventService eventService;

    @Spy
    private ObjectMapper objectMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private Environment environment;

    @Mock
    private CertificatePluginService certificatePluginService;

    @Mock
    private TaskManager taskManager;

    private final static String DOMAIN = "domain1";

    @BeforeClass
    public static void readCertificateSchemaDefinition() throws Exception {
        certificateSchemaDefinition = loadResource("certificate-schema-definition.json");
        certificateConfiguration = loadResource("certificate-configuration.json");
        certificateConfigurationWithOptions = loadResource("certificate-configuration-with-options.json");
    }

    @Before
    public void initCertificateServiceValues() {
        ReflectionTestUtils.setField(certificateService, "delay", 1);
        ReflectionTestUtils.setField(certificateService, "timeUnit", "Minutes");
    }

    private static String loadResource(String name) throws IOException {
        try (InputStream input = CertificateServiceTest.class.getClassLoader().getResourceAsStream(name)) {
            return IOUtil.readLines(input).stream().collect(Collectors.joining());
        }
    }

    @Test
    public void shouldFindById() {
        when(certificateRepository.findById("my-certificate")).thenReturn(Maybe.just(new Certificate()));
        TestObserver testObserver = certificateService.findById("my-certificate").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingCertificate() {
        when(certificateRepository.findById("my-certificate")).thenReturn(Maybe.empty());
        TestObserver testObserver = certificateService.findById("my-certificate").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(certificateRepository.findById("my-certificate")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        certificateService.findById("my-certificate").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(certificateRepository.findByDomain(DOMAIN)).thenReturn(Flowable.just(new Certificate()));
        TestSubscriber<Certificate> testSubscriber = certificateService.findByDomain(DOMAIN).test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(certificateRepository.findByDomain(DOMAIN)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testObserver = certificateService.findByDomain(DOMAIN).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        // prepare certificate
        Certificate certificate = Mockito.mock(Certificate.class);
        when(certificate.getDomain()).thenReturn(DOMAIN);

        when(certificateRepository.findById("my-certificate")).thenReturn(Maybe.just(certificate));
        when(applicationService.findByCertificate("my-certificate")).thenReturn(Flowable.empty());
        when(certificateRepository.delete("my-certificate")).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = certificateService.delete("my-certificate").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateRepository, times(1)).findById("my-certificate");
        verify(applicationService, times(1)).findByCertificate("my-certificate");
        verify(certificateRepository, times(1)).delete("my-certificate");
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldDelete_technicalException() {
        when(certificateRepository.findById("my-certificate")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = certificateService.delete("my-certificate").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(certificateRepository, times(1)).findById("my-certificate");
        verify(applicationService, never()).findByCertificate("my-certificate");
        verify(certificateRepository, never()).delete("my-certificate");
    }

    @Test
    public void shouldDelete_notFound() {
        when(certificateRepository.findById("my-certificate")).thenReturn(Maybe.empty());

        TestObserver testObserver = certificateService.delete("my-certificate").test();

        testObserver.assertError(CertificateNotFoundException.class);
        testObserver.assertNotComplete();

        verify(certificateRepository, times(1)).findById("my-certificate");
        verify(applicationService, never()).findByCertificate("my-certificate");
        verify(certificateRepository, never()).delete("my-certificate");
    }

    @Test
    public void shouldDelete_certificateWithClients() {
        when(certificateRepository.findById("my-certificate")).thenReturn(Maybe.just(new Certificate()));
        when(applicationService.findByCertificate("my-certificate")).thenReturn(Flowable.just(new Application()));

        TestObserver testObserver = certificateService.delete("my-certificate").test();

        testObserver.assertError(CertificateWithApplicationsException.class);
        testObserver.assertNotComplete();

        verify(certificateRepository, times(1)).findById("my-certificate");
        verify(applicationService, times(1)).findByCertificate("my-certificate");
        verify(certificateRepository, never()).delete("my-certificate");
    }

    @Test
    public void shouldCreate_defaultCertificate_Rsa() throws Exception {
        TestObserver<Certificate> testObserver = defaultCertificate(2048, "SHA256withRSA", true);
        testObserver.assertComplete();
    }

    @Test
    public void shouldCreate_defaultCertificate_Ec() throws Exception {
        TestObserver<Certificate> testObserver = defaultCertificate(256, "SHA256withECDSA", true);
        testObserver.assertComplete();
    }

    @Test
    public void unsupportedAlgorithmThrowsException() throws Exception {
        TestObserver<Certificate> testObserver = defaultCertificate(256, "RSASSA-PSS", false);
        testObserver.assertError(IllegalArgumentException.class);
    }

    private TestObserver<Certificate> defaultCertificate(int keySize, String algorithm, boolean shouldBeSuccessful) throws Exception {
        initializeCertificatSettings(keySize, algorithm);

        when(certificateRepository.create(any())).thenReturn(Single.just(new Certificate()));
        when(eventService.create(any(Event.class))).thenReturn(Single.just(new Event()));

        CertificateSchema schema = new CertificateSchema();
        schema.setProperties(Collections.emptyMap());
        doReturn(schema).when(objectMapper).readValue(anyString(), eq(CertificateSchema.class));
        doReturn(mock(ObjectNode.class)).when(objectMapper).createObjectNode();
        doReturn("certificate").when(objectMapper).writeValueAsString(any(ObjectNode.class));
        doReturn(mock(JsonNode.class)).when(objectMapper).readTree(eq("certificate"));

        when(certificatePluginService.getSchema(CertificateServiceImpl.DEFAULT_CERTIFICATE_PLUGIN))
                .thenReturn(Maybe.just(certificateSchemaDefinition));
        TestObserver<Certificate> testObserver = certificateService.create(DOMAIN_NAME).test();
        testObserver.awaitTerminalEvent();

        if (shouldBeSuccessful) {
            verify(certificateRepository).create(argThat(cert -> cert.isSystem()
                    && cert.getDomain().equals(DOMAIN_NAME)
                    && cert.getName().equals("Default")
            ));
        }

        return testObserver;
    }

    private void initializeCertificatSettings(int keySize, String algorithm) {
        when(environment.getProperty(eq("domains.certificates.default.keysize"), any(), any())).thenReturn(keySize);
        when(environment.getProperty(eq("domains.certificates.default.validity"), any(), any())).thenReturn(365);
        when(environment.getProperty(eq("domains.certificates.default.name"), any(), any())).thenReturn("cn=Gravitee.io");
        when(environment.getProperty(eq("domains.certificates.default.algorithm"), any(), any())).thenReturn(algorithm);
        when(environment.getProperty(eq("domains.certificates.default.alias"), any(), any())).thenReturn("default");
        when(environment.getProperty(eq("domains.certificates.default.keypass"), any(), any())).thenReturn("gravitee");
        when(environment.getProperty(eq("domains.certificates.default.storepass"), any(), any())).thenReturn("gravitee");
    }

    @Test
    public void shouldRotate_defaultCertificate_Rsa() throws Exception {
        final var now = LocalDateTime.now();
        final Certificate certOldest = new Certificate();
        certOldest.setSystem(true);
        certOldest.setDomain(DOMAIN);
        certOldest.setName("Cert-1");
        certOldest.setConfiguration(certificateConfiguration);
        certOldest.setType(DEFAULT_CERTIFICATE_PLUGIN);
        certOldest.setCreatedAt(new Date(now.minusYears(3).toInstant(ZoneOffset.UTC).toEpochMilli()));
        certOldest.setExpiresAt(new Date(now.minusYears(2).toInstant(ZoneOffset.UTC).toEpochMilli()));

        final Certificate certIntermediate = new Certificate();
        certIntermediate.setSystem(true);
        certIntermediate.setDomain(DOMAIN);
        certIntermediate.setName("Cert-2");
        certIntermediate.setConfiguration(certificateConfiguration);
        certIntermediate.setType(DEFAULT_CERTIFICATE_PLUGIN);
        certIntermediate.setCreatedAt(new Date(now.minusYears(2).toInstant(ZoneOffset.UTC).toEpochMilli()));
        certIntermediate.setExpiresAt(new Date(now.minusYears(1).toInstant(ZoneOffset.UTC).toEpochMilli()));

        final Certificate certLatest = new Certificate();
        certLatest.setSystem(true);
        certLatest.setId("latest-cert-id");
        certLatest.setDomain(DOMAIN);
        certLatest.setName("Cert-3");
        certLatest.setConfiguration(certificateConfigurationWithOptions);
        certLatest.setType(DEFAULT_CERTIFICATE_PLUGIN);
        certLatest.setCreatedAt(new Date(now.minusYears(1).toInstant(ZoneOffset.UTC).toEpochMilli()));
        certLatest.setExpiresAt(new Date(now.plusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli()));

        final Certificate certCustom = new Certificate();
        certCustom.setSystem(false);
        certCustom.setDomain(DOMAIN);
        certCustom.setName("Cert-4");
        certCustom.setConfiguration(certificateConfiguration);
        certCustom.setType(DEFAULT_CERTIFICATE_PLUGIN);
        certCustom.setCreatedAt(new Date(now.minusYears(1).toInstant(ZoneOffset.UTC).toEpochMilli()));
        certCustom.setExpiresAt(new Date(now.plusDays(10).toInstant(ZoneOffset.UTC).toEpochMilli()));

        when(certificateRepository.findByDomain(eq(DOMAIN))).thenReturn(Flowable.just(certOldest, certLatest, certIntermediate, certCustom));

        initializeCertificatSettings(2048, "SHA256withRSA");

        final Certificate renewedCert = new Certificate();
        renewedCert.setId("renewed-cert-id");
        when(certificateRepository.create(any())).thenReturn(Single.just(renewedCert));
        when(eventService.create(any(Event.class))).thenReturn(Single.just(new Event()));

        CertificateSchema schema = new CertificateSchema();
        schema.setProperties(Collections.emptyMap());
        when(certificatePluginService.getSchema(DEFAULT_CERTIFICATE_PLUGIN))
                .thenReturn(Maybe.just(certificateSchemaDefinition));

        TestObserver<Certificate> testObserver = certificateService.rotate(DOMAIN, mock(User.class)).test();
        testObserver.awaitTerminalEvent();

        verify(certificateRepository).create(argThat(cert -> cert.isSystem()
                && cert.getDomain().equals(DOMAIN)
                && cert.getName().matches("Default\\s[\\d-\\s:]+")
                && cert.getConfiguration().contains("[\"sig\"]")
                && cert.getConfiguration().contains("RS256")
        ));
        verify(taskManager).schedule(argThat(task -> {
            boolean result = task.kind().equals(AssignSystemCertificate.class.getSimpleName());
            result &= task.type().equals(TaskType.SIMPLE);
            var definition = task.getDefinition();
            result &= definition.getDelay() == 1;
            result &= definition.getUnit().equals(TimeUnit.MINUTES);
            if (definition instanceof AssignSystemCertificateDefinition) {
                result &= ((AssignSystemCertificateDefinition) definition).getDomainId().equals(DOMAIN);
                result &= ((AssignSystemCertificateDefinition) definition).getDeprecatedCertificate().equals(certLatest.getId());
                result &= ((AssignSystemCertificateDefinition) definition).getRenewedCertificate() != null;
            } else {
                result = false;
            }
            return result;
        }));
    }

    @Test
    public void shouldRotate_defaultCertificate_Rsa_firstDefault() throws Exception {
        final var now = LocalDateTime.now();

        final Certificate certCustom = new Certificate();
        certCustom.setSystem(false);
        certCustom.setDomain(DOMAIN);
        certCustom.setName("Cert-4");
        certCustom.setConfiguration(certificateConfiguration);
        certCustom.setType(DEFAULT_CERTIFICATE_PLUGIN);
        certCustom.setCreatedAt(new Date(now.minusYears(1).toInstant(ZoneOffset.UTC).toEpochMilli()));
        certCustom.setExpiresAt(new Date(now.plusDays(10).toInstant(ZoneOffset.UTC).toEpochMilli()));

        when(certificateRepository.findByDomain(eq(DOMAIN))).thenReturn(Flowable.just(certCustom));

        initializeCertificatSettings(2048, "SHA256withRSA");

        when(certificateRepository.create(any())).thenReturn(Single.just(new Certificate()));
        when(eventService.create(any(Event.class))).thenReturn(Single.just(new Event()));

        CertificateSchema schema = new CertificateSchema();
        schema.setProperties(Collections.emptyMap());
        when(certificatePluginService.getSchema(DEFAULT_CERTIFICATE_PLUGIN))
                .thenReturn(Maybe.just(certificateSchemaDefinition));

        TestObserver<Certificate> testObserver = certificateService.rotate(DOMAIN, mock(User.class)).test();
        testObserver.awaitTerminalEvent();

        verify(certificateRepository).create(argThat(cert -> cert.isSystem()
                && cert.getDomain().equals(DOMAIN)
                && cert.getName().equals("Default")
                && !cert.getConfiguration().contains("[\"sig\"]")
                && !cert.getConfiguration().contains("RS256")
        ));
        verify(taskManager, never()).schedule(any());
    }
}
