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
package io.gravitee.am.gateway.certificate.impl;

import io.gravitee.am.common.jwt.CertificateInfo;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.certificate.CertificateProviderManager;
import io.gravitee.am.jwt.DefaultJWTBuilder;
import io.gravitee.am.jwt.DefaultJWTParser;
import io.gravitee.am.jwt.NoJWTBuilder;
import io.gravitee.am.jwt.NoJWTParser;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.plugins.certificate.core.CertificateProviderConfiguration;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.Key;
import java.security.KeyPair;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateProviderManagerImpl implements CertificateProviderManager {

    private static final Logger logger = LoggerFactory.getLogger(CertificateProviderManagerImpl.class);
    private final ConcurrentMap<String, CertificateProvider> certificateProviders = new ConcurrentHashMap<>();

    @Autowired
    private CertificatePluginManager certificatePluginManager;

    @Override
    public void create(Certificate certificate) {
        deploy(certificate);
    }

    @Override
    public void update(Certificate certificate) {
        deploy(certificate);
    }

    @Override
    public void delete(String certificateId) {
        undeploy(certificateId);
    }

    @Override
    public CertificateProvider get(String certificateId) {
        return certificateProviders.get(certificateId);
    }


    @Override
    public Collection<CertificateProvider> certificateProviders() {
        return certificateProviders.values();
    }

    @Override
    public CertificateProvider create(io.gravitee.am.certificate.api.CertificateProvider provider, boolean isDefault) {
        // create certificate provider
        CertificateProvider certificateProvider = new CertificateProvider(provider, isDefault);
        try {
            io.gravitee.am.certificate.api.Key providerKey = provider.key().blockingGet();
            certificateProvider.setKeyId(providerKey.getKeyId());
            Object keyValue = providerKey.getValue();
            switch (keyValue) {
                case null -> throw new IllegalArgumentException("Key value cannot be null");
                case KeyPair kp -> {
                    certificateProvider.setJwtBuilder(provider.jwtBuilder().orElseGet(() -> signer(kp.getPrivate(), provider.signatureAlgorithm(), providerKey.getKeyId())));
                    certificateProvider.setJwtParser(provider.jwtParser().orElseGet(() -> verifier(kp.getPublic())));
                }
                case Key key -> {
                    certificateProvider.setJwtBuilder(provider.jwtBuilder().orElseGet(() -> signer(key, provider.signatureAlgorithm(), providerKey.getKeyId())));
                    certificateProvider.setJwtParser(provider.jwtParser().orElseGet(() -> verifier(key)));
                }
                default -> throw new RuntimeException(keyValue + " is not supported");
            }
        } catch (UnsupportedOperationException ex) {
            // alg=none provider
            certificateProvider.setJwtParser(new NoJWTParser());
            certificateProvider.setJwtBuilder(new NoJWTBuilder());
        } catch (Exception ex) {
            logger.error("An error has occurred while creating certificate provider", ex);
            return null;
        }
        return certificateProvider;
    }

    @SneakyThrows
    private DefaultJWTBuilder signer(Key signerKey, String alg, String keyId) {
        return new DefaultJWTBuilder(signerKey, alg, keyId);
    }

    @SneakyThrows
    private DefaultJWTParser verifier(Key verifier) {
        return new DefaultJWTParser(verifier);
    }

    private void deploy(Certificate certificate) {
        // create underline provider
        var providerConfig = new CertificateProviderConfiguration(certificate);
        io.gravitee.am.certificate.api.CertificateProvider provider = certificatePluginManager.create(providerConfig);

        // create certificate provider
        if (provider != null) {
            CertificateProvider certificateProvider = create(provider);
            if (certificateProvider != null) {
                certificateProvider.setCertificateInfo(buildCertificateInfo(certificate));
                certificateProvider.setDomain(certificate.getDomain());
                undeploy(certificate.getId());
                certificateProviders.put(certificate.getId(), certificateProvider);
            }
        } else {
            undeploy(certificate.getId());
        }
    }

    private void undeploy(String certificateId) {
        CertificateProvider removed = certificateProviders.remove(certificateId);
        if(removed != null){
            removed.getProvider().unregister();
        }
    }

    private CertificateInfo buildCertificateInfo(Certificate certificate) {
        return new CertificateInfo(certificate.getId(), certificate.getName());
    }
}
