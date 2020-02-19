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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.certificate.CertificateProviderManager;
import io.gravitee.am.gateway.certificate.jwt.impl.JJWTBuilder;
import io.gravitee.am.gateway.certificate.jwt.impl.JJWTParser;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.JacksonDeserializer;
import io.jsonwebtoken.io.JacksonSerializer;
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

    private final ConcurrentMap<String, CertificateProvider> certificateProviders = new ConcurrentHashMap<>();

    @Autowired
    private CertificatePluginManager certificatePluginManager;

    @Autowired
    private ObjectMapper objectMapper;

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
        certificateProviders.remove(certificateId);
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
    public CertificateProvider create(io.gravitee.am.certificate.api.CertificateProvider provider) {
        // create certificate provider
        CertificateProvider certificateProvider = new CertificateProvider(provider);

        // create parser and builder (default to jjwt)
        io.jsonwebtoken.JwtParser jjwtParser;
        io.jsonwebtoken. JwtBuilder jjwtBuilder;
        try {
            io.gravitee.am.certificate.api.Key providerKey = provider.key().blockingGet();
            Key signingKey = providerKey.getValue() instanceof KeyPair ? ((KeyPair) providerKey.getValue()).getPrivate() : (Key) providerKey.getValue();
            Key verifyingKey = providerKey.getValue() instanceof KeyPair ? ((KeyPair) providerKey.getValue()).getPublic() : (Key) providerKey.getValue();
            jjwtParser = Jwts.parser().deserializeJsonWith(new JacksonDeserializer(objectMapper)).setSigningKey(verifyingKey);
            jjwtBuilder = Jwts.builder().serializeToJsonWith(new JacksonSerializer(objectMapper)).signWith(signingKey).setHeaderParam(JwsHeader.KEY_ID, providerKey.getKeyId());
        } catch (UnsupportedOperationException ex) {
            jjwtParser = Jwts.parser().deserializeJsonWith(new JacksonDeserializer(objectMapper));
            jjwtBuilder = Jwts.builder().serializeToJsonWith(new JacksonSerializer(objectMapper));
        }

        certificateProvider.setJwtParser(new JJWTParser(jjwtParser));
        certificateProvider.setJwtBuilder(new JJWTBuilder(jjwtBuilder));

        return certificateProvider;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private void deploy(Certificate certificate) {
        // create underline provider
        io.gravitee.am.certificate.api.CertificateProvider provider = certificatePluginManager.create(certificate.getType(), certificate.getConfiguration(), certificate.getMetadata());
        // create certificate provider
        if (provider != null) {
            CertificateProvider certificateProvider = create(provider);
            certificateProvider.setDomain(certificate.getDomain());
            certificateProviders.put(certificate.getId(), certificateProvider);
        } else {
            certificateProviders.remove(certificate.getId());
        }
    }
}
