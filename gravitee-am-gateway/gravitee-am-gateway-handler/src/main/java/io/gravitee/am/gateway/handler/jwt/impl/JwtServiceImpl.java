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
package io.gravitee.am.gateway.handler.jwt.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.model.Client;
import io.jsonwebtoken.*;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.Key;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JwtServiceImpl implements JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtServiceImpl.class);

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
        return certificateProvider.key()
                .map(key -> {
                    Key signingKey = key.getValue() instanceof KeyPair ? ((KeyPair) key.getValue()).getPrivate() : (Key) key.getValue();
                    return Jwts.builder().signWith(signingKey).setHeaderParam(JwsHeader.KEY_ID, key.getKeyId()).setClaims(jwt).compact();
                });
    }

    private Single<Map<String, Object>> decode(CertificateProvider certificateProvider, String payload) {
        return certificateProvider.key()
                .map(key -> {
                    Key signingKey = key.getValue() instanceof KeyPair ? ((KeyPair) key.getValue()).getPublic() : (Key) key.getValue();
                    try {
                        JwtParser jwtParser = Jwts.parser().setSigningKey(signingKey);
                        Jwt jwt = jwtParser.parse(payload);

                        if (!jwtParser.isSigned(payload)) {
                            throw new io.gravitee.am.common.jwt.exception.SignatureException("Token is not signed");
                        }

                        return ((Map<String, Object>) jwt.getBody());
                    } catch (ExpiredJwtException ex) {
                        logger.debug("The following JWT token : {} is expired", payload);
                        throw new io.gravitee.am.common.jwt.exception.ExpiredJwtException("Token is expired", ex);
                    } catch (MalformedJwtException ex) {
                        logger.debug("The following JWT token : {} is malformed", payload);
                        throw new io.gravitee.am.common.jwt.exception.MalformedJwtException("Token is malformed", ex);
                    } catch (io.jsonwebtoken.security.SignatureException ex) {
                        logger.debug("Verifying JWT token signature : {} has failed", payload);
                        throw new io.gravitee.am.common.jwt.exception.SignatureException("Token's signature is invalid", ex);
                    } catch (Exception ex) {
                        logger.error("An error occurs while parsing JWT token : {}", payload, ex);
                        throw ex;
                    }
                });
    }

}
