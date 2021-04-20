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

import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.DefaultKey;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.certificate.CertificateProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.common.service.AbstractService;
import io.jsonwebtoken.security.Keys;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import java.security.Key;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateManagerImpl extends AbstractService implements CertificateManager, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(CertificateManagerImpl.class);
    private static final String defaultDigestAlgorithm = "SHA-256";

    @Value("${jwt.secret:s3cR3t4grAv1t3310AMS1g1ingDftK3y}")
    private String signingKeySecret;

    @Value("${jwt.kid:default-gravitee-AM-key}")
    private String signingKeyId;

    @Autowired
    private Domain domain;

    @Autowired
    private CertificateProviderManager certificateProviderManager;

    private CertificateProvider defaultCertificateProvider;

    private CertificateProvider noneAlgorithmCertificateProvider;

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

        Optional<CertificateProvider> certificate =
            this.providers()
                .stream()
                .filter(
                    certificateProvider ->
                        certificateProvider != null &&
                        certificateProvider.getProvider() != null &&
                        algorithm.equals(certificateProvider.getProvider().signatureAlgorithm())
                )
                .findFirst();

        return certificate.isPresent() ? Maybe.just(certificate.get()) : Maybe.empty();
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

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing default certificate provider for domain {}", domain.getName());
        initDefaultCertificateProvider();

        logger.info("Initializing none algorithm certificate provider for domain {}", domain.getName());
        initNoneAlgorithmCertificateProvider();
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
                int keySize = key.getValue().toString().getBytes().length * 8;
                if (keySize >= 512) {
                    return "HS512";
                } else if (keySize >= 384) {
                    return "HS384";
                } else if (keySize >= 256) {
                    return "HS256";
                }
                return null;
            }

            @Override
            public CertificateMetadata certificateMetadata() {
                return certificateMetadata;
            }
        };
        this.defaultCertificateProvider = certificateProviderManager.create(defaultProvider);
    }

    private void initNoneAlgorithmCertificateProvider() {
        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, "none"));

        io.gravitee.am.certificate.api.CertificateProvider noneProvider = new io.gravitee.am.certificate.api.CertificateProvider() {
            @Override
            public Single<io.gravitee.am.certificate.api.Key> key() {
                throw new UnsupportedOperationException("No key for \"none\" algorithm");
            }

            @Override
            public Single<String> publicKey() {
                throw new UnsupportedOperationException("No public key for \"none\" algorithm");
            }

            @Override
            public Flowable<JWK> keys() {
                throw new UnsupportedOperationException("No keys for \"none\" algorithm");
            }

            @Override
            public String signatureAlgorithm() {
                return "none";
            }

            @Override
            public CertificateMetadata certificateMetadata() {
                return certificateMetadata;
            }
        };
        this.noneAlgorithmCertificateProvider = certificateProviderManager.create(noneProvider);
    }
}
