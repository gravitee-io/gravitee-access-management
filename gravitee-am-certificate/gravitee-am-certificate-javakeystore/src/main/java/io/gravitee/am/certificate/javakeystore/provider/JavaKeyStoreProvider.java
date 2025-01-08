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
package io.gravitee.am.certificate.javakeystore.provider;

import io.gravitee.am.certificate.api.AbstractCertificateProvider;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.javakeystore.JavaKeyStoreConfiguration;
import io.gravitee.am.common.plugin.ValidationResult;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static io.gravitee.am.common.plugin.ValidationResult.invalid;
import static io.gravitee.am.common.plugin.ValidationResult.valid;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */

public class JavaKeyStoreProvider extends AbstractCertificateProvider implements InitializingBean {

    @Autowired
    private JavaKeyStoreConfiguration configuration;

    @Override
    public void afterPropertiesSet() throws Exception {
        createCertificateKeys(certificateMetadata());
    }

    @Override
    protected String invalidCertificateFileMessage() {
        return "A jks file is required to use Java KeyStore certificate";
    }

    @Override
    protected KeyStore keyStore() throws KeyStoreException {
        return KeyStore.getInstance(KeyStore.getDefaultType());
    }

    @Override
    public CertificateMetadata certificateMetadata() {
        return certificateMetadata;
    }

    @Override
    protected String getStorepass() {
        return configuration.getStorepass();
    }

    @Override
    protected String getAlias() {
        return configuration.getAlias();
    }

    @Override
    protected String getKeypass() {
        return configuration.getKeypass();
    }

    @Override
    protected Set<String> getUse() {
        return configuration.getUse();
    }

    @Override
    protected String getAlgorithm() {
        return configuration.getAlgorithm();
    }

    @Override
    public ValidationResult validate() {
        Date expDate = getExpirationDate().orElse(null);
        if(expDate == null) {
            return invalid("The certificate you uploaded lacks expiration date.");
        }
        if (Instant.now().isAfter(expDate.toInstant())) {
            return invalid("The certificate you uploaded has already expired. Please select a different certificate to upload.");
        }
        return valid(Map.of("expDate", expDate));
    }
}
