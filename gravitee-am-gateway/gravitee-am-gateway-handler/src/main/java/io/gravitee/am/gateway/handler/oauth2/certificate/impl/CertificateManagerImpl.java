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

import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.certificate.api.DefaultKey;
import io.gravitee.am.gateway.core.event.DomainEvent;
import io.gravitee.am.gateway.handler.oauth2.certificate.CertificateManager;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.jsonwebtoken.security.Keys;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.security.Key;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateManagerImpl extends AbstractService implements CertificateManager, InitializingBean, EventListener<DomainEvent, Domain> {

    private static final Logger logger = LoggerFactory.getLogger(CertificateManagerImpl.class);
    private static final String defaultDigestAlgorithm = "SHA-256";

    @Value("${jwt.secret:s3cR3t4grAv1t3310AMS1g1ingDftK3y}")
    private String signingKeySecret;

    @Value("${jwt.kid:default-gravitee-AM-key}")
    private String signingKeyId;

    @Autowired
    private Domain domain;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificatePluginManager certificatePluginManager;

    @Autowired
    private EventManager eventManager;

    private ConcurrentMap<String, Map<String, CertificateProvider>> domainsCertificateProviders = new ConcurrentHashMap<>();

    private CertificateProvider defaultCertificateProvider;

    @Override
    public Maybe<CertificateProvider> get(String id) {
        return id == null ? Maybe.empty() : findByDomainAndId(domain.getId(), id);
    }

    @Override
    public Maybe<CertificateProvider> findByDomainAndId(String domain, String id) {
        return id == null ? Maybe.empty() : Observable.fromIterable(domainsCertificateProviders.get(domain).entrySet())
                .filter(certificateProviderEntry -> certificateProviderEntry.getKey().equals(id))
                .firstElement()
                .map(Map.Entry::getValue);
    }

    @Override
    public Collection<CertificateProvider> providers() {
        return domainsCertificateProviders
                .entrySet()
                .stream()
                .flatMap(p -> p.getValue().entrySet().stream().map(Map.Entry::getValue))
                .collect(Collectors.toList());
    }

    @Override
    public CertificateProvider defaultCertificateProvider() {
        return defaultCertificateProvider;
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing default certificate provider for domain {}", domain.getName());
        initDefaultCertificateProvider();

        logger.info("Initializing certificates for domain {}", domain.getName());
        certificateRepository.findAll()
                .subscribe(
                        certificates -> {
                            certificates.forEach(certificate -> {
                                if (certificate.getDomain().equals(domain.getId())) {
                                    logger.info("Initializing certificate: {} [{}]", certificate.getName(), certificate.getType());
                                }
                                updateCertificateProvider(certificate);
                            });
                            logger.info("Certificates loaded for domain {}", domain.getName());
                        },
                        error -> logger.error("Unable to initialize certificates for domain {}", domain.getName(), error));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register certificate manager event listener for cross domain events");
        eventManager.subscribeForEvents(this, DomainEvent.class);
    }

    @Override
    public void onEvent(Event<DomainEvent, Domain> event) {
        Domain updatedDomain = event.content();
        if (!updatedDomain.getId().equals(domain.getId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateDomainCertificateProviders(updatedDomain);
                    break;
                case UNDEPLOY:
                    domainsCertificateProviders.remove(updatedDomain.getId());
                    break;
            }
        }
    }

    private void updateDomainCertificateProviders(Domain updatedDomain) {
        logger.info("Domain {} has received domain event from domain {}, update its certificates", domain.getName(), updatedDomain.getName());
        certificateRepository.findByDomain(updatedDomain.getId())
                .subscribe(
                        certificates -> {
                            certificates.forEach(certificate -> {
                                logger.info("\tInitializing certificate: {} [{}]", certificate.getName(), certificate.getType());
                                updateCertificateProvider(certificate);
                            });
                            logger.info("Certificates updated for domain {}", updatedDomain.getName());
                        },
                        error -> logger.error("Unable to update certificates for domain {}", updatedDomain.getName(), error));
    }

    private void updateCertificateProvider(Certificate certificate) {
        // create certificate provider
        CertificateProvider certificateProvider = certificatePluginManager.create(certificate.getType(), certificate.getConfiguration(), certificate.getMetadata());

        // add certificate provider to its domain
        Map<String, CertificateProvider> existingDomainCertificateProviders = domainsCertificateProviders.get(certificate.getDomain());
        if (existingDomainCertificateProviders != null) {
            Map<String, CertificateProvider> updateCertificateProviders = new HashMap<>(existingDomainCertificateProviders);
            updateCertificateProviders.put(certificate.getId(), certificateProvider);
            domainsCertificateProviders.put(certificate.getDomain(), updateCertificateProviders);
        } else {
            domainsCertificateProviders.put(certificate.getDomain(), Collections.singletonMap(certificate.getId(), certificateProvider));
        }
    }

    private void initDefaultCertificateProvider() {
        // create default signing HMAC key
        Key key = Keys.hmacShaKeyFor(signingKeySecret.getBytes());
        io.gravitee.am.certificate.api.Key certificateKey = new DefaultKey(signingKeyId, key);

        // create default certificate provider
        setDefaultCertificateProvider(certificateKey);
    }

    private void setDefaultCertificateProvider(io.gravitee.am.certificate.api.Key key) {
        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, defaultDigestAlgorithm));

        defaultCertificateProvider = new CertificateProvider() {

            @Override
            public Single<io.gravitee.am.certificate.api.Key> key() {
                return Single.just(key);
            }

            @Override
            public Single<String> publicKey() {
                return null;
            }

            @Override
            public Flowable<JWK> keys() {
                return null;
            }

            @Override
            public CertificateMetadata certificateMetadata() {
                return certificateMetadata;
            }
        };
    }
}
