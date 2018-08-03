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
package io.gravitee.am.gateway.handler.oauth2.certificate.impl;

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.gateway.handler.oauth2.certificate.CertificateManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateManagerImpl implements CertificateManager, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(CertificateManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificatePluginManager certificatePluginManager;

    private Map<String, CertificateProvider> certificateProviders = new HashMap<>();

    @Override
    public Maybe<CertificateProvider> get(String id) {
        CertificateProvider certificateProvider = certificateProviders.get(id);
        return (certificateProvider != null) ? Maybe.just(certificateProvider) : Maybe.empty();
    }

    @Override
    public Collection<CertificateProvider> providers() {
        return certificateProviders.values();
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing certificates for domain {}", domain.getName());

        certificateRepository.findByDomain(domain.getId())
                .subscribe(certificates -> {
                    certificates.forEach(certificate -> {
                        logger.info("\tInitializing certificate: {} [{}]", certificate.getName(), certificate.getType());

                        CertificateProvider certificateProvider =
                                certificatePluginManager.create(certificate.getType(), certificate.getConfiguration());
                        certificateProviders.put(certificate.getId(), certificateProvider);
                    });
                    logger.info("Certificates loaded for domain {}", domain.getName());
                }, error -> logger.error("Unable to initialize certificates for domain {}", domain.getName(), error));
    }
}
