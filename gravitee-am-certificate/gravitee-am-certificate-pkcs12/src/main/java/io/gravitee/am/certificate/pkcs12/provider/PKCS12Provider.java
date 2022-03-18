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

import io.gravitee.am.certificate.api.AbstractCertificateProvider;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.pkcs12.PKCS12Configuration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PKCS12Provider extends AbstractCertificateProvider implements InitializingBean {

    private final static String KEYSTORE_TYPE = "pkcs12";
    @Autowired
    private PKCS12Configuration configuration;
    @Autowired
    private CertificateMetadata certificateMetadata;

    @Override
    public void afterPropertiesSet() throws Exception {
        createCertificateKeys(certificateMetadata());
    }

    @Override
    protected String invalidCertificateFileMessage() {
        return "A .p12 / .pfx  file is required to use PKCS#12 certificate";
    }

    @Override
    protected KeyStore keyStore() throws KeyStoreException {
        return KeyStore.getInstance(KEYSTORE_TYPE);
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
}
