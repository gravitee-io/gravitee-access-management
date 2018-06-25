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
package io.gravitee.am.management.handlers.management.api.certificate;

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.service.CertificateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
// TODO : should be removed after refactoring display certificate public key feature
public class CertificateManager implements InitializingBean  {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateManager.class);

    @Autowired
    private CertificatePluginManager certificatePluginManager;

    @Autowired
    private CertificateService certificateService;

    public void reloadCertificateProviders(Certificate certificate) {
        CertificateProvider certificateProvider = certificatePluginManager.create(certificate.getType(), certificate.getConfiguration());
        certificateService.setCertificateProvider(certificate.getId(), certificateProvider);
    }

    @Override
    public void afterPropertiesSet() {
        LOGGER.info("Initializing certificate providers");
        Map<String, CertificateProvider> certificateProviders = new HashMap<>();
        List<Certificate> certificates = certificateService.findAll().blockingGet();
        certificates.forEach(certificate -> {
            LOGGER.info("\tInitializing certificate: {} [{}]", certificate.getName(), certificate.getType());

            CertificateProvider certificateProvider =
                    certificatePluginManager.create(certificate.getType(), certificate.getConfiguration());
            certificateProviders.put(certificate.getId(), certificateProvider);
        });

        certificateService.setCertificateProviders(certificateProviders);
    }
}
