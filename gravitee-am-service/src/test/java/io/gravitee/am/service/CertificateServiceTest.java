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

import com.fasterxml.jackson.core.JsonProcessingException;
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
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.am.service.tasks.AssignSystemCertificate;
import io.gravitee.am.service.tasks.AssignSystemCertificateDefinition;
import io.gravitee.am.service.tasks.TaskType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import static java.time.temporal.ChronoUnit.HOURS;
import java.time.temporal.TemporalUnit;
import java.util.Base64;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.mockStatic;
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

    @Mock
    private KeyStore keyStore;

    private final static String DOMAIN = "domain1";

    private final static String EXPIRED_CERT = "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";

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
        TestObserver<Certificate> testObserver = certificateService.findById("my-certificate").test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingCertificate() {
        when(certificateRepository.findById("my-certificate")).thenReturn(Maybe.empty());
        TestObserver<Certificate> testObserver = certificateService.findById("my-certificate").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(certificateRepository.findById("my-certificate")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver<Certificate> testObserver = new TestObserver();
        certificateService.findById("my-certificate").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(certificateRepository.findByDomain(DOMAIN)).thenReturn(Flowable.just(new Certificate()));
        TestSubscriber<Certificate> testSubscriber = certificateService.findByDomain(DOMAIN).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(certificateRepository.findByDomain(DOMAIN)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber<Certificate> testObserver = certificateService.findByDomain(DOMAIN).test();

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

        TestObserver<Void> testObserver = certificateService.delete("my-certificate").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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

        TestObserver<Void> testObserver = certificateService.delete("my-certificate").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(certificateRepository, times(1)).findById("my-certificate");
        verify(applicationService, never()).findByCertificate("my-certificate");
        verify(certificateRepository, never()).delete("my-certificate");
    }

    @Test
    public void shouldDelete_notFound() {
        when(certificateRepository.findById("my-certificate")).thenReturn(Maybe.empty());

        TestObserver<Void> testObserver = certificateService.delete("my-certificate").test();

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

        TestObserver<Void> testObserver = certificateService.delete("my-certificate").test();

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
    public void shouldNotCreateWhenCertificateIsExpired() throws JsonProcessingException {
        when(certificatePluginService.getSchema(CertificateServiceImpl.DEFAULT_CERTIFICATE_PLUGIN))
                .thenReturn(Maybe.just(certificateSchemaDefinition));
        var certificateNode = objectMapper.createObjectNode();
        var contentNode = objectMapper.createObjectNode();
        contentNode.put("content", EXPIRED_CERT);
        contentNode.put("name", "test.p12");
        contentNode.put("type", "application/pkcs12");
        certificateNode.put("content", objectMapper.writeValueAsString(contentNode));
        certificateNode.put("alias", "am-server");
        certificateNode.put("storepass", "server-secret");
        certificateNode.put("keypass", "server-secret");
        var newCertificate = new NewCertificate();
        newCertificate.setName("expired-certificate");
        newCertificate.setType(DEFAULT_CERTIFICATE_PLUGIN);
        newCertificate.setConfiguration(certificateNode.toString());
        TestObserver<Certificate> testObserver = certificateService.create(DOMAIN_NAME, newCertificate, Mockito.mock(User.class)).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(error -> error instanceof CertificateException && "Uploading certificate is already expired".equals(error.getMessage()));
    }

    @Test
    public void shouldNotCreateWhenCertificateFileIsNotFound(){
        when(certificatePluginService.getSchema(CertificateServiceImpl.DEFAULT_CERTIFICATE_PLUGIN)).thenReturn(Maybe.just(certificateSchemaDefinition));
        var certificateNode = objectMapper.createObjectNode();
        certificateNode.put("alias", "am-server");
        certificateNode.put("storepass", "server-secret");
        certificateNode.put("keypass", "server-secret");
        var newCertificate = new NewCertificate();
        newCertificate.setName("without-file");
        newCertificate.setType(DEFAULT_CERTIFICATE_PLUGIN);
        newCertificate.setConfiguration(certificateNode.toString());
        TestObserver<Certificate> testObserver = certificateService.create(DOMAIN_NAME, newCertificate, Mockito.mock(User.class)).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(error -> error instanceof CertificateException && "Certification file is not found".equals(error.getMessage()));
    }

    @Test
    public void unsupportedAlgorithmThrowsException() throws Exception {
        TestObserver<Certificate> testObserver = defaultCertificate(256, "RSASSA-PSS", false);
        testObserver.assertError(IllegalArgumentException.class);
    }

    private TestObserver<Certificate> defaultCertificate(int keySize, String algorithm, boolean shouldBeSuccessful) throws Exception {
        TestObserver<Certificate> testObserver;
        initializeCertificatSettings(keySize, algorithm);
        when(certificatePluginService.getSchema(CertificateServiceImpl.DEFAULT_CERTIFICATE_PLUGIN)).thenReturn(Maybe.just(certificateSchemaDefinition));
        var certificateNode = objectMapper.createObjectNode();
        var contentNode = objectMapper.createObjectNode();
        contentNode.put("content", Base64.getEncoder().encode("file-content-cert".getBytes(StandardCharsets.UTF_8)));
        contentNode.put("name", "test.p12");
        contentNode.put("type", "application/pkcs12");
        certificateNode.put("content", objectMapper.writeValueAsString(contentNode));
        certificateNode.put("alias", "am-server");
        certificateNode.put("storepass", "server-secret");
        certificateNode.put("keypass", "server-secret");
        doReturn(new ObjectMapper().writeValueAsString(certificateNode)).when(objectMapper).writeValueAsString(any(ObjectNode.class));
        when(certificateRepository.create(any())).thenReturn(Single.just(new Certificate()));
        when(eventService.create(any(Event.class))).thenReturn(Single.just(new Event()));
        doReturn(mock(ObjectNode.class)).when(objectMapper).createObjectNode();
        when(certificatePluginService.getSchema(CertificateServiceImpl.DEFAULT_CERTIFICATE_PLUGIN))
                .thenReturn(Maybe.just(certificateSchemaDefinition));
        var certificate = mock(X509Certificate.class);
        doReturn(certificate).when(keyStore).getCertificate(any());
        when(certificate.getNotAfter()).thenReturn(new Date(Instant.now().plus(1, HOURS).toEpochMilli()));
         try (MockedStatic<KeyStore> mocked = mockStatic(KeyStore.class)) {
            mocked.when(() -> KeyStore.getInstance(any())).thenReturn(keyStore);
            assertEquals(keyStore, KeyStore.getInstance("123"));

            testObserver = certificateService.create(DOMAIN_NAME).test();
            testObserver.awaitDone(10, TimeUnit.SECONDS);

            mocked.verify(() -> KeyStore.getInstance("PKCS12"), times(shouldBeSuccessful ? 1 : 0));
        }
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
    public void shouldRotate_defaultCertificate_Rsa() {
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

        when(certificateRepository.findByDomain(DOMAIN)).thenReturn(Flowable.just(certOldest, certLatest, certIntermediate, certCustom));

        initializeCertificatSettings(2048, "SHA256withRSA");

        final Certificate renewedCert = new Certificate();
        renewedCert.setId("renewed-cert-id");
        when(certificateRepository.create(any())).thenReturn(Single.just(renewedCert));
        when(eventService.create(any(Event.class))).thenReturn(Single.just(new Event()));

        when(certificatePluginService.getSchema(DEFAULT_CERTIFICATE_PLUGIN))
                .thenReturn(Maybe.just(certificateSchemaDefinition));

        TestObserver<Certificate> testObserver = certificateService.rotate(DOMAIN, mock(User.class)).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
    public void shouldRotate_defaultCertificate_Rsa_firstDefault() {
        final var now = LocalDateTime.now();

        final Certificate certCustom = new Certificate();
        certCustom.setSystem(false);
        certCustom.setDomain(DOMAIN);
        certCustom.setName("Cert-4");
        certCustom.setConfiguration(certificateConfiguration);
        certCustom.setType(DEFAULT_CERTIFICATE_PLUGIN);
        certCustom.setCreatedAt(new Date(now.minusYears(1).toInstant(ZoneOffset.UTC).toEpochMilli()));
        certCustom.setExpiresAt(new Date(now.plusDays(10).toInstant(ZoneOffset.UTC).toEpochMilli()));

        when(certificateRepository.findByDomain(DOMAIN)).thenReturn(Flowable.just(certCustom));

        initializeCertificatSettings(2048, "SHA256withRSA");

        when(certificateRepository.create(any())).thenReturn(Single.just(new Certificate()));
        when(eventService.create(any(Event.class))).thenReturn(Single.just(new Event()));

        when(certificatePluginService.getSchema(DEFAULT_CERTIFICATE_PLUGIN))
                .thenReturn(Maybe.just(certificateSchemaDefinition));

        TestObserver<Certificate> testObserver = certificateService.rotate(DOMAIN, mock(User.class)).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        verify(certificateRepository).create(argThat(cert -> cert.isSystem()
                && cert.getDomain().equals(DOMAIN)
                && cert.getName().equals("Default")
                && !cert.getConfiguration().contains("[\"sig\"]")
                && !cert.getConfiguration().contains("RS256")
        ));
        verify(taskManager, never()).schedule(any());
    }
}
