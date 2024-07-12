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
package io.gravitee.am.certificate.api;

import io.gravitee.am.model.Certificate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.certificate.api.ConfigurationCertUtils.configurationStringAsMap;
import static io.gravitee.am.certificate.api.DefaultTrustStoreProvider.getDefaultTrustStorePassword;
import static io.gravitee.am.certificate.api.DefaultTrustStoreProvider.getDefaultTrustStorePath;

@RequiredArgsConstructor
@Getter
@Slf4j
public class UnsecuredKeyStore {
    private final KeyStore keyStore;
    private final String alias;
    private final char[] password;

    @SneakyThrows
    public static UnsecuredKeyStore load(Certificate certificate) {
        byte[] value = (byte[]) certificate.getMetadata().get(CertificateMetadata.FILE);
        Map<String, Object> cfg = configurationStringAsMap(certificate.getConfiguration());

        String alias = (String) cfg.get("alias");
        String password = (String) cfg.get("keypass");

        KeyStore keyStore = KeyStore.getInstance("jks");
        try (InputStream is = new ByteArrayInputStream(value)){
            keyStore.load(is, password.toCharArray());
        }
        return new UnsecuredKeyStore(keyStore, alias, password.toCharArray());
    }

    @SneakyThrows
    public static UnsecuredKeyStore loadFromFile(String path, String password) {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream trustStoreStream = new FileInputStream(path)) {
            keyStore.load(trustStoreStream, password.toCharArray());
        }

        return new UnsecuredKeyStore(keyStore, null, password.toCharArray());
    }

    public static Optional<UnsecuredKeyStore> loadDefaultTrustStore() {
        try {
            UnsecuredKeyStore ks = UnsecuredKeyStore.loadFromFile(getDefaultTrustStorePath(), getDefaultTrustStorePassword());
            return Optional.of(ks);
        } catch (Exception e){
            log.warn("TrustStore not loaded, path: {} (passwd set: {})", getDefaultTrustStorePath(), getDefaultTrustStorePassword() != null);
            return Optional.empty();
        }
    }

}
