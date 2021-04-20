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
package io.gravitee.am.gateway.certificate;

import io.gravitee.am.gateway.core.manager.EntityManager;
import io.gravitee.am.model.Certificate;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultCertificateManager implements EntityManager<Certificate> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultCertificateManager.class);
    private final ConcurrentMap<String, Certificate> certificates = new ConcurrentHashMap<>();

    @Autowired
    private CertificateProviderManager certificateProviderManager;

    @Override
    public void deploy(Certificate certificate) {
        try {
            certificateProviderManager.create(certificate);
            certificates.put(certificate.getId(), certificate);
            logger.info("Certificate {} for domain {} loaded", certificate.getName(), certificate.getDomain());
        } catch (Exception ex) {
            logger.error("Unable to load certificate {} for domain {}", certificate.getName(), certificate.getDomain(), ex);
        }
    }

    @Override
    public void update(Certificate certificate) {
        try {
            certificates.put(certificate.getId(), certificate);
            certificateProviderManager.create(certificate);
            logger.info("Certificate {} for domain {} updated", certificate.getId(), certificate.getDomain());
        } catch (Exception ex) {
            logger.error("Unable to update certificate {} for domain {}", certificate.getName(), certificate.getDomain(), ex);
            certificates.remove(certificate.getId());
            certificateProviderManager.delete(certificate.getId());
        }
    }

    @Override
    public void undeploy(String certificateId) {
        certificates.remove(certificateId);
        certificateProviderManager.delete(certificateId);
        logger.info("Certificate {} undeployed", certificateId);
    }

    @Override
    public Collection<Certificate> entities() {
        return certificates.values();
    }

    @Override
    public Certificate get(String certificateId) {
        return certificates.get(certificateId);
    }

    public void init(Collection<Certificate> certificates) {
        certificates.forEach(this::deploy);
    }
}
