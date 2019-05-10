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
package io.gravitee.am.gateway.handler.common.jwt.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.Client;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWTServiceImpl implements JWTService {

    private static final Logger logger = LoggerFactory.getLogger(JWTServiceImpl.class);

    @Autowired
    private CertificateManager certificateManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Single<String> encode(JWT jwt, CertificateProvider certificateProvider) {
        Objects.requireNonNull(certificateProvider, "Certificate provider is required to sign JWT");
        return sign(certificateProvider, jwt);
    }

    @Override
    public Single<String> encode(JWT jwt, Client client) {
        return certificateManager.get(client.getCertificate())
                .defaultIfEmpty(certificateManager.defaultCertificateProvider())
                .flatMapSingle(certificateProvider -> encode(jwt, certificateProvider));
    }

    @Override
    public Single<String> encodeUserinfo(JWT jwt, Client client) {
        //Userinfo may not be signed but only encrypted
        if(client.getUserinfoSignedResponseAlg()==null) {
            return encode(jwt,certificateManager.noneAlgorithmCertificateProvider());
        }

        return certificateManager.findByAlgorithm(client.getUserinfoSignedResponseAlg())
                .switchIfEmpty(certificateManager.get(client.getCertificate()))
                .defaultIfEmpty(certificateManager.defaultCertificateProvider())
                .flatMapSingle(certificateProvider -> encode(jwt, certificateProvider));
    }

    @Override
    public Single<JWT> decodeAndVerify(String jwt, Client client) {
        // use findByDomainAndId method because introspect token can be use across domains
        return certificateManager.findByDomainAndId(client.getDomain(), client.getCertificate())
                .defaultIfEmpty(certificateManager.defaultCertificateProvider())
                .flatMapSingle(certificateProvider -> decode(certificateProvider, jwt))
                .map(claims -> new JWT(claims));
    }

    @Override
    public Single<JWT> decode(String jwt) {
        return Single.create(emitter -> {
            try {
                String json = new String(Base64.getDecoder().decode(jwt.split("\\.")[1]), "UTF-8");
                emitter.onSuccess(objectMapper.readValue(json, JWT.class));
            } catch (Exception ex) {
                logger.debug("Failed to decode JWT", ex);
                emitter.onError(new InvalidTokenException("The access token is invalid", ex));
            }
        });

    }

    private Single<String> sign(CertificateProvider certificateProvider, JWT jwt) {
        return Single.just(certificateProvider.getJwtBuilder().sign(jwt));
    }

    private Single<Map<String, Object>> decode(CertificateProvider certificateProvider, String payload) {
        return Single.just(certificateProvider.getJwtParser().parse(payload));
    }

}
