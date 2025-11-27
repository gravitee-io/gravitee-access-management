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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.text.ParseException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

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
                .flatMap(certificateProvider -> encode(jwt, certificateProvider));
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
                .flatMap(certificateProvider -> encode(jwt, certificateProvider));
    }

    @Override
    public Single<String> encodeAuthorization(JWT jwt, Client client) {
        // Signing an authorization response is required
        // As per https://bitbucket.org/openid/fapi/src/master/Financial_API_JWT_Secured_Authorization_Response_Mode.md#markdown-header-5-client-metadata
        // If unspecified, the default algorithm to use for signing authorization responses is RS256. The algorithm none is not allowed.
        String signedResponseAlg = client.getAuthorizationSignedResponseAlg();

        // To ensure backward compatibility
        if (signedResponseAlg == null) {
            signedResponseAlg = JWSAlgorithm.RS256.getName();
        }

        return certificateManager.findByAlgorithm(signedResponseAlg)
                .switchIfEmpty(certificateManager.get(client.getCertificate()))
                .defaultIfEmpty(certificateManager.defaultCertificateProvider())
                .flatMap(certificateProvider -> encode(jwt, certificateProvider));
    }

    @Override
    public Single<JWT> decodeAndVerify(String jwt, Supplier<String> getDefaultCertificateId, TokenType tokenType) {
        if (getDefaultCertificateId == null) {
            return Single.error(new IllegalArgumentException("getDefaultCertificateId is required"));
        }
        Optional<SignedJWT> signedJWTOptional = parseSignedToken(jwt);
        Optional<String> issuerDomain = extractDomain(signedJWTOptional);

        return Maybe.fromOptional(extractKid(signedJWTOptional).flatMap(kid -> certificateManager.providers()
                        .stream()
                        .filter(certificateProvider -> kid.equals(certificateProvider.getProvider().getAlias()) &&
                                (issuerDomain.isEmpty() || issuerDomain.get().equals(certificateProvider.getDomain())))
                        .findFirst()))
                .switchIfEmpty(certificateManager.get(getDefaultCertificateId.get()))
                .defaultIfEmpty(certificateManager.defaultCertificateProvider())
                .flatMap(certificateProvider -> decodeAndVerify(jwt, certificateProvider, tokenType));
    }

    private static Optional<SignedJWT> parseSignedToken(String jwt) {
        try {
            return Optional.ofNullable(SignedJWT.parse(jwt));
        } catch (ParseException e) {
            logger.debug("Unable to parse JWT to verify it", e);
            return Optional.empty();
        }
    }

    @Override
    public Single<JWT> decodeAndVerify(String jwt, CertificateProvider certificateProvider, TokenType tokenType) {
        return decode(certificateProvider, jwt, tokenType)
                .map(JWT::new);
    }

    @Override
    public Single<JWT> decode(String jwt, TokenType tokenType) {
        return Single.create(emitter -> {
            try {
                String json = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), "UTF-8");
                emitter.onSuccess(objectMapper.readValue(json, JWT.class));
            } catch (Exception ex) {
                logger.debug("Failed to decode {} JWT", tokenType, ex);
                emitter.onError(buildInvalidTokenException(tokenType, ex));
            }
        });
    }

    private static InvalidTokenException buildInvalidTokenException(TokenType tokenType, Exception ex) {
        switch (tokenType) {
            case STATE:
                return new InvalidTokenException("The state token is invalid", ex);
            case ID_TOKEN:
                return new InvalidTokenException("The id token is invalid", ex);
            case REFRESH_TOKEN:
                return new InvalidTokenException("The refresh token is invalid", ex);
            case SESSION:
                return new InvalidTokenException("The session token is invalid", ex);
            default:
                return new InvalidTokenException("The access token is invalid", ex);
        }
    }

    private Single<String> sign(CertificateProvider certificateProvider, JWT jwt) {
        return Single.create(emitter -> {
            try {
                String encodedToken = certificateProvider.getJwtBuilder().sign(jwt);
                emitter.onSuccess(encodedToken);
            } catch (Exception ex) {
                logger.error("Failed to sign JWT", ex);
                emitter.onError(new InvalidTokenException("The JWT token couldn't be signed", ex));
            }
        });
    }

    private Single<Map<String, Object>> decode(CertificateProvider certificateProvider, String payload, TokenType tokenType) {
        return Single.create(emitter -> {
            try {
                Map<String, Object> decodedPayload = certificateProvider.getJwtParser().parse(payload);
                emitter.onSuccess(decodedPayload);
            } catch (Exception ex) {
                logger.debug("Failed to decode {} JWT", tokenType, ex);
                emitter.onError(buildInvalidTokenException(tokenType, ex));
            }
        });
    }

    private Optional<String> extractKid(Optional<SignedJWT> jwt) {
        return jwt.map(SignedJWT::getHeader).map(header -> header.getKeyID());
    }
    private Optional<String> extractDomain(Optional<SignedJWT>  jwt) {
        if (jwt.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(jwt.get().getJWTClaimsSet().getStringClaim("domain"));
        } catch (ParseException e) {
            logger.debug("Unable to parse JWT to extract domain", e);
            return Optional.empty();
        }
    }
}
