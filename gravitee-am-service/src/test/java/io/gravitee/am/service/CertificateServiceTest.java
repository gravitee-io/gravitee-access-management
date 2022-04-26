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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.plugins.certificate.core.schema.CertificateSchema;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.CertificateServiceImpl;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateServiceTest {

    @InjectMocks
    private CertificateService certificateService = new CertificateServiceImpl();

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private EventService eventService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private Environment environment;

    @Mock
    private CertificatePluginService certificatePluginService;

    private final static String DOMAIN = "domain1";

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

        testObserver.assertError(TechnicalManagementException.class);
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

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(certificateRepository, times(1)).findById("my-certificate");
        verify(applicationService, times(1)).findByCertificate("my-certificate");
        verify(certificateRepository, never()).delete("my-certificate");
    }

    @Test
    public void shouldCreate_defaultCertificate_Rsa() throws Exception {
        TestObserver<Certificate> testObserver = defaultCertificate(2048, "SHA256withRSA");
        testObserver.assertComplete();
    }

    @Test
    public void shouldCreate_defaultCertificate_Ec() throws Exception {
        TestObserver<Certificate> testObserver = defaultCertificate(256, "SHA256withECDSA");
        testObserver.assertComplete();
    }

    @Test
    public void unsupportedAlgorithmThrowsException() throws Exception {
        TestObserver<Certificate> testObserver = defaultCertificate(256, "RSASSA-PSS");
        testObserver.assertError(IllegalArgumentException.class);
    }

    private TestObserver<Certificate> defaultCertificate(int keySize, String algorithm) throws Exception {
        when(environment.getProperty(eq("domains.certificates.default.keysize"), any(), any())).thenReturn(keySize);
        when(environment.getProperty(eq("domains.certificates.default.validity"), any(), any())).thenReturn(365);
        when(environment.getProperty(eq("domains.certificates.default.name"), any(), any())).thenReturn("cn=Gravitee.io");
        when(environment.getProperty(eq("domains.certificates.default.algorithm"), any(), any())).thenReturn(algorithm);
        when(environment.getProperty(eq("domains.certificates.default.alias"), any(), any())).thenReturn("default");
        when(environment.getProperty(eq("domains.certificates.default.keypass"), any(), any())).thenReturn("gravitee");
        when(environment.getProperty(eq("domains.certificates.default.storepass"), any(), any())).thenReturn("gravitee");

        when(certificateRepository.create(any())).thenReturn(Single.just(new Certificate()));
        when(eventService.create(any(Event.class))).thenReturn(Single.just(new Event()));

        CertificateSchema schema = new CertificateSchema();
        schema.setProperties(Collections.emptyMap());
        when(objectMapper.readValue(anyString(), eq(CertificateSchema.class))).thenReturn(schema);
        when(objectMapper.createObjectNode()).thenReturn(mock(ObjectNode.class));
        when(objectMapper.writeValueAsString(any(ObjectNode.class))).thenReturn("certificate");
        when(certificatePluginService.getSchema(CertificateServiceImpl.DEFAULT_CERTIFICATE_PLUGIN))
                .thenReturn(Maybe.just("{\n" +
                        "  \"type\" : \"object\",\n" +
                        "  \"id\" : \"urn:jsonschema:io:gravitee:am:certificate:pkcs12:PKCS12Configuration\",\n" +
                        "  \"properties\" : {\n" +
                        "    \"content\" : {\n" +
                        "      \"title\": \"PKCS#12 file\",\n" +
                        "      \"description\": \"PKCS file\",\n" +
                        "      \"type\" : \"string\",\n" +
                        "      \"widget\" : \"file\"\n" +
                        "    },\n" +
                        "    \"storepass\" : {\n" +
                        "      \"title\": \"Keystore password\",\n" +
                        "      \"description\": \"The password which is used to protect the integrity of the keystore.\",\n" +
                        "      \"type\" : \"string\"\n" +
                        "    },\n" +
                        "    \"alias\" : {\n" +
                        "      \"title\": \"Key alias\",\n" +
                        "      \"description\": \"Alias which identify the keystore entry.\",\n" +
                        "      \"type\" : \"string\"\n" +
                        "    },\n" +
                        "    \"keypass\" : {\n" +
                        "      \"title\": \"Key password\",\n" +
                        "      \"description\": \"The password used to protect the private key of the generated key pair.\",\n" +
                        "      \"type\" : \"string\"\n" +
                        "    }\n" +
                        "  },\n" +
                        "  \"required\": [\n" +
                        "    \"content\",\n" +
                        "    \"storepass\",\n" +
                        "    \"alias\",\n" +
                        "    \"keypass\"\n" +
                        "  ]\n" +
                        "}"));
        TestObserver<Certificate> testObserver = certificateService.create("my-domain").test();
        testObserver.awaitTerminalEvent();
//        testObserver.assertComplete();
        return testObserver;
    }
}
