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
import com.nimbusds.jose.JOSEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;

import com.nimbusds.jose.util.Base64URL;
import io.gravitee.am.common.crypto.CryptoUtils;
import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.EncodedJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.JwtSignerExecutor;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.schedulers.Schedulers;
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

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWTServiceImpl implements JWTService {

    private static final Logger logger = LoggerFactory.getLogger(JWTServiceImpl.class);
    public static final String AWS_HSM_CERTIFICATE_PROVIDER = "AwsHsmCertificateProvider";


    private final CertificateManager certificateManager;
    private final ObjectMapper objectMapper;
    private final Boolean fallbackToHmacSignature;

    @Autowired
    public JWTServiceImpl(CertificateManager certificateManager,
                          ObjectMapper objectMapper,
                          Boolean fallbackToHmacSignature) {
        this.certificateManager = certificateManager;
        this.objectMapper = objectMapper;
        this.fallbackToHmacSignature = fallbackToHmacSignature;
    }

    @Autowired
    private JwtSignerExecutor executor;

    @Override
    public Single<EncodedJWT> encodeJwt(JWT jwt, CertificateProvider certificateProvider) {
        Objects.requireNonNull(certificateProvider, "Certificate provider is required to sign JWT");
        var claimsToEncrypt = Claims.requireEncryption();
        if (claimsToEncrypt.stream().noneMatch(jwt::containsKey)) {
            return signWithCertificateInfo(certificateProvider, jwt);
        }
        return certificateProvider.getProvider()
                .key()
                .map(key -> {
                    if (key.getValue() instanceof Key singleKey) {
                        return singleKey;
                    } else if (key.getValue() instanceof KeyPair keyPair) {
                        return keyPair.getPrivate();
                    } else {
                        throw new IllegalArgumentException("Invalid key type: " + key.getValue().getClass());
                    }
                })
                .map(key -> {
                    claimsToEncrypt.forEach(claim -> encryptClaim(jwt, claim, key));
                    return jwt;
                }).flatMap(token -> signWithCertificateInfo(certificateProvider, token));
    }

    private void encryptClaim(JWT jwt, String claim, java.security.Key key) {
        if (!jwt.containsKey(claim)) {
            return;
        }
        jwt.put(claim, CryptoUtils.encrypt((String) jwt.get(claim), key));
    }

    @Override
    public Single<EncodedJWT> encodeJwt(JWT jwt, Client client) {
        return certificateManager.getClientCertificateProvider(client, fallbackToHmacSignature)
                .flatMap(certificateProvider -> encodeJwt(jwt, certificateProvider));
    }

    @Override
    public Single<String> encode(JWT jwt, Client client) {
        return certificateManager.getClientCertificateProvider(client, fallbackToHmacSignature)
                .flatMap(certificateProvider -> encode(jwt, certificateProvider));
    }

    @Override
    public Single<EncodedJWT> encodeUserinfo(JWT jwt, Client client) {
        //Userinfo may not be signed but only encrypted
        if (client.getUserinfoSignedResponseAlg() == null) {
            return encodeJwt(jwt, certificateManager.noneAlgorithmCertificateProvider());
        }

        return certificateManager.findByAlgorithm(client.getUserinfoSignedResponseAlg())
                .switchIfEmpty(certificateManager.getClientCertificateProvider(client, fallbackToHmacSignature))
                .flatMap(certificateProvider -> encodeJwt(jwt, certificateProvider));
    }

    @Override
    public Single<EncodedJWT> encodeAuthorization(JWT jwt, Client client) {
        // Signing an authorization response is required
        // As per https://bitbucket.org/openid/fapi/src/master/Financial_API_JWT_Secured_Authorization_Response_Mode.md#markdown-header-5-client-metadata
        // If unspecified, the default algorithm to use for signing authorization responses is RS256. The algorithm none is not allowed.
        String signedResponseAlg = client.getAuthorizationSignedResponseAlg();

        // To ensure backward compatibility
        if (signedResponseAlg == null) {
            signedResponseAlg = JWSAlgorithm.RS256.getName();
        }

        return certificateManager.findByAlgorithm(signedResponseAlg)
                .switchIfEmpty(certificateManager.getClientCertificateProvider(client, fallbackToHmacSignature))
                .flatMap(certificateProvider -> encodeJwt(jwt, certificateProvider));
    }

    @Override
    public Single<JWT> decodeAndVerify(String jwt, Supplier<String> getDefaultCertificateId, TokenType tokenType) {
        if (getDefaultCertificateId == null) {
            return Single.error(new IllegalArgumentException("getDefaultCertificateId is required"));
        }
        String certificateId = extractKid(jwt).orElseGet(getDefaultCertificateId);
        return certificateManager.get(certificateId)
                .switchIfEmpty(Single.defer(() -> {
                    logger.warn("Falling back to default certificate provider for certificateId: {}", certificateId);
                    return Single.just(certificateManager.defaultCertificateProvider());
                }))
                .flatMap(certificateProvider -> decodeAndVerify(jwt, certificateProvider, tokenType));
    }

    @Override
    public Single<JWT> decodeAndVerify(String jwt, Client client, TokenType tokenType) {
        return certificateManager.getClientCertificateProvider(client, fallbackToHmacSignature)
                .flatMap(certificateProvider -> decodeAndVerify(jwt, certificateProvider, tokenType));
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
                String json = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
                emitter.onSuccess(objectMapper.readValue(json, JWT.class));
            } catch (Exception ex) {
                logger.debug("Failed to decode {} JWT", tokenType, ex);
                emitter.onError(buildInvalidTokenException(tokenType, ex));
            }
        });
    }

    private static InvalidTokenException buildInvalidTokenException(TokenType tokenType, Exception ex) {
        return switch (tokenType) {
            case STATE -> new InvalidTokenException("The state token is invalid", ex);
            case ID_TOKEN -> new InvalidTokenException("The id token is invalid", ex);
            case REFRESH_TOKEN -> new InvalidTokenException("The refresh token is invalid", ex);
            case SESSION -> new InvalidTokenException("The session token is invalid", ex);
            default -> new InvalidTokenException("The access token is invalid", ex);
        };
    }

    private Single<EncodedJWT> signWithCertificateInfo(CertificateProvider certificateProvider, JWT jwt) {
        return sign(certificateProvider, jwt)
                .map(encodedValue -> new EncodedJWT(encodedValue, certificateProvider.getCertificateInfo()));
    }

    private Single<String> sign(CertificateProvider certificateProvider, JWT jwt) {
        final var signer = Single.create((SingleEmitter<String> emitter) -> {
            try {
                String encodedToken = certificateProvider.getJwtBuilder().sign(jwt);
                emitter.onSuccess(encodedToken);
            } catch (Exception ex) {
                logger.error("Failed to sign JWT", ex);
                emitter.onError(new InvalidTokenException("The JWT token couldn't be signed", ex));
            }
        });

        if (certificateProvider.getProvider().getClass().getSimpleName().equals(AWS_HSM_CERTIFICATE_PROVIDER)) {
            return signer.subscribeOn(Schedulers.from(executor.getExecutor())).observeOn(Schedulers.computation());
        } else {
            return signer;
        }
    }

    private Single<Map<String, Object>> decode(CertificateProvider certificateProvider, String payload, TokenType tokenType) {
        final var verifier = Single.create((SingleEmitter<Map<String, Object>> emitter) -> {
            try {
                Map<String, Object> decodedPayload = certificateProvider.getJwtParser().parse(payload);
                emitter.onSuccess(decodedPayload);
            } catch (Exception ex) {
                logger.debug("Failed to decode {} JWT", tokenType, ex);
                emitter.onError(buildInvalidTokenException(tokenType, ex));
            }
        });

        if (certificateProvider.getProvider().getClass().getSimpleName().equals(AWS_HSM_CERTIFICATE_PROVIDER)) {
            return verifier.subscribeOn(Schedulers.from(executor.getExecutor())).observeOn(Schedulers.computation());
        } else {
            return verifier;
        }
    }

    private Optional<String> extractKid(String jwt) {
        final String parameterName = "kid";
        try {
            Base64URL[] parts = JOSEObject.split(jwt);
            JWSHeader header = JWSHeader.parse(parts[0]);
            Object value = header.toJSONObject().get(parameterName);
            return value instanceof String stringValue && RandomString.isUuid(stringValue)
                ? Optional.of(stringValue)
                : Optional.empty();
        } catch (IllegalArgumentException | ParseException e) {
            logger.debug("Unable to parse JWT header to extract {}", parameterName, e);
            return Optional.empty();
        }
    }
}
