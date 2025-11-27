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
package io.gravitee.am.certificate.pkcs12.provider;

import com.nimbusds.jose.jwk.KeyUse;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.pkcs12.PKCS12Configuration;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Set;

import static java.util.Map.of;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class PKCS12ProviderTest {

    private static final String CERTIFICATE_ID = "cert123";

    @ParameterizedTest
    @ValueSource(strings = {"/server-no-extension.p12", "/server-sign-extension.p12"})
    public void should_have_use_with_sig_default(String file) throws Exception {
        final var provider = loadProvider(file, null);

        final var jwk = provider.keys().blockingFirst();
        Assertions.assertEquals(KeyUse.SIGNATURE.getValue(), jwk.getUse());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/server-no-extension.p12", "/server-sign-extension.p12"})
    public void should_have_use_with_sig_config_empty(String file) throws Exception {
        final var provider = loadProvider(file, Set.of());
        final var jwk = provider.keys().blockingFirst();
        Assertions.assertEquals(KeyUse.SIGNATURE.getValue(), jwk.getUse());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/server-no-extension.p12", "/server-sign-extension.p12"})
    public void should_have_use_with_enc_config(String file) throws Exception {
        final var provider = loadProvider(file, Set.of(KeyUse.ENCRYPTION.getValue()));
        final var jwk = provider.keys().blockingFirst();
        Assertions.assertEquals(KeyUse.ENCRYPTION.getValue(), jwk.getUse());
    }

    @Test
    public void should_have_certificate_key_id() throws Exception {
        final var provider = loadProvider("/server-no-extension.p12", null);
        Assertions.assertEquals(CERTIFICATE_ID, provider.key().blockingGet().getKeyId());
    }

    private PKCS12Provider loadProvider(String certificate, Set<String> use) throws Exception {
        final var provider = new PKCS12Provider();
        PKCS12Configuration config = new PKCS12Configuration();
        config.setContent("name.p12");
        config.setAlias("am-server");
        config.setKeypass("server-secret");
        config.setStorepass("server-secret");
        config.setUse(use);
        CertificateMetadata metadata = new CertificateMetadata();
        try (InputStream certReader = this.getClass().getResourceAsStream(certificate)) {
            assert certReader != null;
            final var content = certReader.readAllBytes();
            metadata.setMetadata(new HashMap<>(of(CertificateMetadata.FILE, content, CertificateMetadata.ID, CERTIFICATE_ID)));
        }

        ReflectionTestUtils.setField(provider, "certificateMetadata", metadata);
        ReflectionTestUtils.setField(provider, "configuration", config);

        provider.afterPropertiesSet();
        return provider;
    }
}
