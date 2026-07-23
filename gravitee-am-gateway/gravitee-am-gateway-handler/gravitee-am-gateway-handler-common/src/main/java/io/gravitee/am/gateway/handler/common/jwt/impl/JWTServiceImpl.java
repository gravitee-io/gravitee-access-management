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
import io.gravitee.am.common.crypto.CryptoUtils;
import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.exception.oauth2.TemporarilyUnavailableException;
import io.gravitee.am.common.jwt.CertificateInfo;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.EncodedJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.JwtSignerExecutor;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.text.ParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import lombok.CustomLog;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@CustomLog
public class JWTServiceImpl implements JWTService {


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
                .flatMap(certificateProvider -> encodeJwtWithFallback(jwt, certificateProvider));
    }

    @Override
    public Single<String> encode(JWT jwt, Client client) {
        return certificateManager.getClientCertificateProvider(client, fallbackToHmacSignature)
                .flatMap(certificateProvider -> encodeWithFallback(jwt, certificateProvider));
    }

    @Override
    public Single<EncodedJWT> encodeUserinfo(JWT jwt, Client client) {
        //Userinfo may not be signed but only encrypted
        if (client.getUserinfoSignedResponseAlg() == null) {
            return encodeJwt(jwt, certificateManager.noneAlgorithmCertificateProvider());
        }

        return certificateManager.findByAlgorithm(client.getUserinfoSignedResponseAlg())
                .switchIfEmpty(certificateManager.getClientCertificateProvider(client, fallbackToHmacSignature))
                .flatMap(certificateProvider -> encodeJwtWithFallback(jwt, certificateProvider));
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
                .flatMap(certificateProvider -> encodeJwtWithFallback(jwt, certificateProvider));
    }

    @Override
    public Single<JWT> decodeAndVerify(String jwt, Maybe<String> clientCertificateId, TokenType tokenType) {
        if (clientCertificateId == null) {
            return Single.error(new IllegalArgumentException("clientCertificateId is required"));
        }
        List<CertificateProvider> kidProviders = parseSignedToken(jwt)
                .map(this::findProvidersByKid)
                .orElse(List.of());
        if (!kidProviders.isEmpty()) {
            return verifyWithAnyProvider(jwt, kidProviders, tokenType);
        }
        return resolveClientCertificateProvider(clientCertificateId)
                .flatMap(certificateProvider -> decodeAndVerify(jwt, certificateProvider, tokenType));
    }

    private static Optional<SignedJWT> parseSignedToken(String jwt) {
        try {
            return Optional.of(SignedJWT.parse(jwt));
        } catch (ParseException e) {
            log.debug("Unable to parse JWT to verify it", e);
            return Optional.empty();
        }
    }

    private List<CertificateProvider> findProvidersByKid(SignedJWT signedJWT) {
        String kid = signedJWT.getHeader().getKeyID();
        if (kid == null) {
            return List.of();
        }

        String issuerDomain = extractDomain(signedJWT);

        return Stream.concat(certificateManager.providers().stream(), Stream.of(certificateManager.defaultCertificateProvider()))
                .filter(Objects::nonNull)
                .filter(provider -> kid.equals(provider.getKeyId()) || kid.equals(ofNullable(provider.getCertificateInfo()).map(CertificateInfo::certificateId).orElse(null)))
                .filter(provider -> issuerDomain == null || issuerDomain.equals(provider.getDomain()) || provider.isDefaultCertificate())
                .collect(Collectors.toList());
    }

    private Single<JWT> verifyWithAnyProvider(String jwt, List<CertificateProvider> providers, TokenType tokenType) {
        return providers.stream()
                .map(provider -> decodeAndVerify(jwt, provider, tokenType))
                .reduce((chain, next) -> chain.onErrorResumeNext(err -> next))
                .get();
    }

    private static String extractDomain(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet().getStringClaim("domain");
        } catch (ParseException e) {
            log.debug("Unable to parse JWT claims to extract domain", e);
            return null;
        }
    }

    private Single<CertificateProvider> resolveClientCertificateProvider(Maybe<String> clientCertificateId) {
        return clientCertificateId
                .flatMap(certificateId -> certificateManager.get(certificateId)
                        .switchIfEmpty(fallbackToHmacSignature
                                ? Maybe.just(certificateManager.defaultCertificateProvider())
                                : Maybe.error(new TemporarilyUnavailableException("The certificate cannot be loaded"))))
                .switchIfEmpty(Single.just(certificateManager.defaultCertificateProvider()));
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
                log.debug("Failed to decode {} JWT", tokenType, ex);
                emitter.onError(buildInvalidTokenException(tokenType, ex));
            }
        });
    }

    private static InvalidTokenException buildInvalidTokenException(TokenType tokenType, Exception ex) {
        return switch (tokenType) {
            case STATE -> new InvalidTokenException("The state token is invalid", ex);
            case ID_TOKEN -> new InvalidTokenException("The id token is invalid", ex);
            case REFRESH_TOKEN -> new InvalidTokenException("The refresh token is invalid", ex);
            case JWT -> new InvalidTokenException("The jwt token is invalid", ex);
            case SESSION -> new InvalidTokenException("The session token is invalid", ex);
            default -> new InvalidTokenException("The access token is invalid", ex);
        };
    }

    private Single<EncodedJWT> encodeJwtWithFallback(JWT jwt, CertificateProvider certificateProvider) {
        return encodeJwt(jwt, certificateProvider)
                .onErrorResumeNext(error -> certificateManager.fallbackCertificateProvider()
                        .filter(fallback -> !Objects.equals(fallback.getCertificateInfo().certificateId(),
                                certificateProvider.getCertificateInfo().certificateId()))
                        .doOnSuccess(fallback -> onFallbackUsed(certificateProvider, fallback))
                        .flatMapSingle(fallback -> encodeJwt(jwt, fallback))
                        .switchIfEmpty(Single.error(error)));
    }

    private Single<String> encodeWithFallback(JWT jwt, CertificateProvider certificateProvider) {
        return encode(jwt, certificateProvider)
                .onErrorResumeNext(error -> certificateManager.fallbackCertificateProvider()
                        .filter(fallback -> !Objects.equals(fallback.getCertificateInfo().certificateId(),
                                certificateProvider.getCertificateInfo().certificateId()))
                        .doOnSuccess(fallback -> onFallbackUsed(certificateProvider, fallback))
                        .flatMapSingle(fallback -> encode(jwt, fallback))
                        .switchIfEmpty(Single.error(error)));
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
                log.error("Failed to sign JWT", ex);
                emitter.onError(new InvalidTokenException("The JWT token couldn't be signed", ex));
            }
        });

        if (certificateProvider.getProvider().useBlockingSigner()) {
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
                log.debug("Failed to decode {} JWT", tokenType, ex);
                emitter.onError(buildInvalidTokenException(tokenType, ex));
            }
        });

        if (certificateProvider.getProvider().useBlockingSigner()) {
            return verifier.subscribeOn(Schedulers.from(executor.getExecutor())).observeOn(Schedulers.computation());
        } else {
            return verifier;
        }
    }

    private static void onFallbackUsed(CertificateProvider originalCertificateProvider, CertificateProvider fallback) {
        log.warn("Failed to sign JWT with certificate: {}, attempting fallback using: {}",
                originalCertificateProvider.getCertificateInfo().certificateId(),
                fallback.getCertificateInfo().certificateId());
    }
}
