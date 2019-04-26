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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.DefaultKey;
import io.gravitee.am.gateway.core.event.CertificateEvent;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.jwt.impl.JJWTBuilder;
import io.gravitee.am.gateway.handler.common.jwt.impl.JJWTParser;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.JacksonDeserializer;
import io.jsonwebtoken.io.JacksonSerializer;
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
import java.security.KeyPair;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateManagerImpl extends AbstractService implements CertificateManager, InitializingBean, EventListener<CertificateEvent, Payload> {

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

    @Autowired
    private ObjectMapper objectMapper;

    private ConcurrentMap<String, Map<String, CertificateProvider>> domainsCertificateProviders = new ConcurrentHashMap<>();

    private CertificateProvider defaultCertificateProvider;

    @Override
    public Maybe<CertificateProvider> get(String id) {
        return id == null ? Maybe.empty() : findByDomainAndId(domain.getId(), id);
    }

    @Override
    public Maybe<CertificateProvider> findByAlgorithm(String algorithm) {

        if(algorithm==null || algorithm.trim().isEmpty()) {
            return Maybe.empty();
        }

        Optional<CertificateProvider> certificate = this
                .providers()
                .stream()
                .filter(certificateProvider ->
                        certificateProvider!=null && certificateProvider.getProvider()!=null &&
                        algorithm.equals(certificateProvider.getProvider().signatureAlgorithm())
                )
                .findFirst();

        return certificate.isPresent()?Maybe.just(certificate.get()):Maybe.empty();
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
        if (!domainsCertificateProviders.containsKey(domain.getId())) {
            return Collections.emptyList();
        }

        return domainsCertificateProviders
                .get(domain.getId())
                .entrySet()
                .stream()
                .map(p -> p.getValue())
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

        logger.info("Register event listener for certificate events");
        eventManager.subscribeForEvents(this, CertificateEvent.class);
    }

    @Override
    public void onEvent(Event<CertificateEvent, Payload> event) {
        switch (event.type()) {
            case DEPLOY:
            case UPDATE:
                updateCertificate(event.content().getId(), event.type());
                break;
            case UNDEPLOY:
                removeCertificate(event.content().getId(), event.content().getDomain());
                break;
        }
    }

    private void updateCertificate(String certificateId, CertificateEvent certificateEvent) {
        final String eventType = certificateEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} certificate event for {}", domain.getName(), eventType, certificateId);
        certificateRepository.findById(certificateId)
                .subscribe(
                        certificate -> {
                            updateCertificateProvider(certificate);
                            logger.info("Certificate {} {}d for domain {}", certificateId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} certificate for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No certificate found with id {}", certificateId));
    }

    private void removeCertificate(String certificateId, String domainId) {
        logger.info("Domain {} has received certificate event, delete certificate {}", domain.getName(), certificateId);
        domainsCertificateProviders.get(domainId).remove(certificateId);
    }

    private void updateCertificateProvider(Certificate certificate) {
        // create underline provider
        io.gravitee.am.certificate.api.CertificateProvider provider = certificatePluginManager.create(certificate.getType(), certificate.getConfiguration(), certificate.getMetadata());

        // create certificate provider
        CertificateProvider certificateProvider = create(provider);

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

        io.gravitee.am.certificate.api.CertificateProvider defaultProvider = new io.gravitee.am.certificate.api.CertificateProvider() {

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
            public String signatureAlgorithm() {
                int keySize = key.getValue().toString().getBytes().length*8;
                if(keySize>=512) {
                    return "HS512";
                }
                else if(keySize>=384) {
                    return "HS384";
                }
                else if(keySize>=256) {
                    return "HS256";
                }
                return null;
            }

            @Override
            public CertificateMetadata certificateMetadata() {
                return certificateMetadata;
            }
        };

        defaultCertificateProvider = create(defaultProvider);
    }

    private CertificateProvider create(io.gravitee.am.certificate.api.CertificateProvider provider) {
        // create certificate provider
        CertificateProvider certificateProvider = new CertificateProvider(provider);

        // create parser and builder (default to jjwt)
        io.gravitee.am.certificate.api.Key providerKey = provider.key().blockingGet();
        Key signingKey = providerKey.getValue() instanceof KeyPair ? ((KeyPair) providerKey.getValue()).getPrivate() : (Key) providerKey.getValue();
        Key verifyingKey = providerKey.getValue() instanceof KeyPair ? ((KeyPair) providerKey.getValue()).getPublic() : (Key) providerKey.getValue();

        io.jsonwebtoken.JwtParser jjwtParser = Jwts.parser().deserializeJsonWith(new JacksonDeserializer(objectMapper)).setSigningKey(verifyingKey);
        io.jsonwebtoken. JwtBuilder jjwtBuilder = Jwts.builder().serializeToJsonWith(new JacksonSerializer(objectMapper)).signWith(signingKey).setHeaderParam(JwsHeader.KEY_ID, providerKey.getKeyId());

        certificateProvider.setJwtParser(new JJWTParser(jjwtParser));
        certificateProvider.setJwtBuilder(new JJWTBuilder(jjwtBuilder));

        return certificateProvider;
    }
}
