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
package io.gravitee.am.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.gravitee.am.management.service.impl.upgrades.CertificateUpgrader;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.service.CertificateService;
import io.reactivex.Single;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateUpgraderTest {

    @InjectMocks
    private CertificateUpgrader certificateUpgrader = new CertificateUpgrader();

    @Mock
    private CertificateService certificateService;

    @Mock
    private ObjectMapper objectMapper;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldUpdateCertificates_noBinaryFile() throws IOException {
        // create folder
        folder.newFolder("test");
        File file = folder.newFile("test/server.jks");

        Certificate certificate = new Certificate();
        certificate.setName("certificate-test");
        certificate.setType("javakeystore-am-certificate");
        certificate.setConfiguration("{\"jks\":\""+file.getAbsolutePath()+"\"}");

        JsonNode jsonNode = mock(JsonNode.class);
        TextNode textNode = mock(TextNode.class);
        when(textNode.asText()).thenReturn(file.getAbsolutePath());
        when(jsonNode.get("jks")).thenReturn(textNode);
        when(objectMapper.readTree(anyString())).thenReturn(jsonNode);
        when(certificateService.findAll()).thenReturn(Single.just(Collections.singletonList(certificate)));
        when(certificateService.update(any())).thenReturn(Single.just(new Certificate()));

        certificateUpgrader.upgrade();

        verify(certificateService, times(1)).findAll();
        verify(certificateService, times(1)).update(any());
    }

    @Test
    public void shouldNotUpdateCertificates_binaryFile() {
        Certificate certificate = new Certificate();
        certificate.setName("certificate-test");
        certificate.setType("javakeystore-am-certificate");
        certificate.setMetadata(Collections.singletonMap("file", new File("")));

        when(certificateService.findAll()).thenReturn(Single.just(Collections.singletonList(certificate)));
        certificateUpgrader.upgrade();

        verify(certificateService, times(1)).findAll();
        verify(certificateService, never()).update(any());
    }

    @Test
    public void shouldNotUpdateCertificates_noJavaKeyStoreCertificate() {
        Certificate certificate = new Certificate();
        certificate.setName("certificate-test");
        certificate.setType("unknown");
        certificate.setMetadata(Collections.singletonMap("file", new File("")));

        when(certificateService.findAll()).thenReturn(Single.just(Collections.singletonList(certificate)));
        certificateUpgrader.upgrade();

        verify(certificateService, times(1)).findAll();
        verify(certificateService, never()).update(any());
    }
}
