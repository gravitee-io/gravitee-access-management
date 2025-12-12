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
package io.gravitee.am.gateway.handler.common.certificate.impl;

import io.gravitee.am.certificate.api.CertificateProviders;
import io.gravitee.am.common.event.CertificateEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.certificate.CertificateProviderManager;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderCertificateReloader;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.configuration.Configuration;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.InvalidKeyException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static io.gravitee.am.common.utils.ConstantKeys.DEFAULT_JWT_OR_CSRF_SECRET;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateManagerImpl extends AbstractService implements CertificateManager, EventListener<CertificateEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(CertificateManagerImpl.class);

    @Autowired
    private Configuration configuration;

    @Autowired
    private Domain domain;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private IdentityProviderCertificateReloader identityProviderReloader;

    @Autowired
    private CertificateProviderManager certificateProviderManager;

    @Autowired
    private io.gravitee.am.monitoring.DomainReadinessService domainReadinessService;

    private CertificateProvider defaultCertificateProvider;

    private CertificateProvider noneAlgorithmCertificateProvider;

    private final ConcurrentMap<String, Certificate> certificates = new ConcurrentHashMap<>();

    @Override
    public void onEvent(Event<CertificateEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN &&
                domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    deployCertificate(event.content().getId());
                    break;
                case UNDEPLOY:
                    removeCertificate(event.content().getId());
                    break;
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        initialize();

        logger.info("Register event listener for certificate events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, CertificateEvent.class, domain.getId());
    }

    private void initialize() throws Exception {
        logger.info("Initializing default certificate provider for domain {}", domain.getName());
        initDefaultCertificateProvider();
        logger.info("Default certificate loaded for domain {}", domain.getName());

        logger.info("Initializing none algorithm certificate provider for domain {}", domain.getName());
        initNoneAlgorithmCertificateProvider();
        logger.info("None algorithm certificate loaded for domain {}", domain.getName());

        logger.info("Initializing certificates for domain {}", domain.getName());
        certificateRepository.findByDomain(domain.getId())
                .observeOn(Schedulers.io())
                .subscribe(
                        certificate -> {
                            domainReadinessService.initPluginSync(domain.getId(), certificate.getId(), Type.CERTIFICATE.name());
                            certificateProviderManager.create(certificate);
                            certificates.put(certificate.getId(), certificate);
                            logger.info("Certificate {} loaded for domain {}", certificate.getName(), domain.getName());
                            domainReadinessService.pluginLoaded(domain.getId(), certificate.getId());
                        },
                        error ->
                                logger.error("An error has occurred when loading certificates for domain {}", domain.getName(), error)
                );
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for certificate events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, CertificateEvent.class, domain.getId());
        
        for (Map.Entry<String, Certificate> entry: certificates.entrySet()) {
            certificateProviderManager.delete(entry.getKey());
            domainReadinessService.pluginUnloaded(domain.getId(), entry.getKey());
        }
        certificates.clear();
    }

    @Override
    public Maybe<CertificateProvider> get(String id) {
        if (id == null) {
            return Maybe.empty();
        }
        CertificateProvider certificateProvider = certificateProviderManager.get(id);
        return certificateProvider != null ? Maybe.just(certificateProvider) : Maybe.empty();
    }

    @Override
    public io.gravitee.am.certificate.api.CertificateProvider getCertificate(String id) {
        CertificateProvider certificateProvider = certificateProviderManager.get(id);
        if (certificateProvider != null && domain.getId().equals(certificateProvider.getDomain())) {
            return certificateProvider.getProvider();
        }
        return null;
    }

    @Override
    public Maybe<CertificateProvider> findByAlgorithm(String algorithm) {

        if (algorithm == null || algorithm.trim().isEmpty()) {
            return Maybe.empty();
        }

        Optional<CertificateProvider> certificate = this
                .providers()
                .stream()
                .filter(certificateProvider ->
                        certificateProvider != null && certificateProvider.getProvider() != null &&
                                algorithm.equals(certificateProvider.getProvider().signatureAlgorithm())
                )
                .findFirst();

        return certificate.map(Maybe::just).orElseGet(Maybe::empty);
    }

    @Override
    public Collection<CertificateProvider> providers() {
        return certificateProviderManager
                .certificateProviders()
                .stream()
                .filter(c -> domain.getId().equals(c.getDomain()))
                .collect(Collectors.toList());
    }

    @Override
    public CertificateProvider defaultCertificateProvider() {
        return defaultCertificateProvider;
    }

    @Override
    public CertificateProvider noneAlgorithmCertificateProvider() {
        return noneAlgorithmCertificateProvider;
    }

    private void deployCertificate(String certificateId) {
        logger.info("Deploying certificate {} for domain {}", certificateId, domain.getName());
        certificateRepository.findById(certificateId)
                .observeOn(Schedulers.io())
                .subscribe(
                        certificate -> {
                            try {
                                certificateProviderManager.create(certificate);
                                certificates.put(certificateId, certificate);
                                logger.info("Certificate {} loaded for domain {}", certificateId, domain.getName());
                                domainReadinessService.pluginLoaded(domain.getId(), certificateId);
                                reloadIdentityProviders(certificate);
                            } catch (Exception ex) {
                                logger.error("Unable to load certificate {} for domain {}", certificate.getName(), certificate.getDomain(), ex);
                                certificates.remove(certificateId, certificate);
                                domainReadinessService.pluginFailed(domain.getId(), certificateId, ex.getMessage());
                            }
                        },
                        error -> {
                            logger.error("An error has occurred when loading certificate {} for domain {}", certificateId, domain.getName(), error);
                            domainReadinessService.pluginFailed(domain.getId(), certificateId, error.getMessage());
                        },
                        () -> logger.error("No certificate found with id {}", certificateId));
    }

    private void reloadIdentityProviders(Certificate certificate){
        identityProviderReloader
                .reloadIdentityProvidersWithCertificate(certificate.getId())
                .subscribe();
    }

    private void removeCertificate(String certificateId) {
        logger.info("Removing certificate {} for domain {}", certificateId, domain.getName());
        Certificate deletedCertificate = certificates.remove(certificateId);
        certificateProviderManager.delete(certificateId);
        domainReadinessService.pluginUnloaded(domain.getId(), certificateId);
        if (deletedCertificate != null) {
            logger.info("Certificate {} has been removed for domain {}", certificateId, domain.getName());
        } else {
            logger.info("Certificate {} was not loaded for domain {}", certificateId, domain.getName());
        }
    }

    private void initDefaultCertificateProvider() throws InvalidKeyException {
        this.defaultCertificateProvider = certificateProviderManager.create(CertificateProviders.createShaCertificateProvider(signingKeyId(), signingKeySecret()));
    }

    private void initNoneAlgorithmCertificateProvider() {
        this.noneAlgorithmCertificateProvider = certificateProviderManager.create(CertificateProviders.createNoneCertificateProvider());
    }

    private String signingKeySecret() {
        return configuration.getProperty("jwt.secret", DEFAULT_JWT_OR_CSRF_SECRET);
    }

    private String signingKeyId() {
        return configuration.getProperty("jwt.kid", "default-gravitee-AM-key");
    }
}
