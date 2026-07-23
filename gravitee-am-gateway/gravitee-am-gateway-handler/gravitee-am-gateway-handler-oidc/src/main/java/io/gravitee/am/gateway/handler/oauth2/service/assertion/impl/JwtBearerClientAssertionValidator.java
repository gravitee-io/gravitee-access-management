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
package io.gravitee.am.gateway.handler.oauth2.service.assertion.impl;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionValidator;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.gateway.handler.oidc.service.spiffe.SpiffeJwtSvidValidator;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.reactivex.rxjava3.core.Maybe;
import lombok.CustomLog;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static io.gravitee.am.gateway.handler.oauth2.service.assertion.impl.JwtAssertionSupport.NOT_VALID;
import static io.gravitee.am.gateway.handler.oauth2.service.assertion.impl.JwtAssertionSupport.parseJwt;
import static io.gravitee.am.gateway.handler.oauth2.service.assertion.impl.JwtAssertionSupport.unableToValidateClient;
import static io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils.isSignAlgCompliantWithFapi;
import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * Standard RFC 7523 {@code jwt-bearer} client assertion: {@code iss == sub == client_id},
 * signature verified against the client's JWKS (private_key_jwt) or a shared
 * secret (client_secret_jwt).
 */
@CustomLog
public class JwtBearerClientAssertionValidator implements ClientAssertionValidator {

    private final ClientLookupService clientLookupService;
    private final JWKService jwkService;
    private final JWSService jwsService;
    private final OpenIDDiscoveryService openIDDiscoveryService;
    private final Domain domain;

    public JwtBearerClientAssertionValidator(ClientLookupService clientLookupService,
                                             JWKService jwkService,
                                             JWSService jwsService,
                                             OpenIDDiscoveryService openIDDiscoveryService,
                                             Domain domain) {
        this.clientLookupService = clientLookupService;
        this.jwkService = jwkService;
        this.jwsService = jwsService;
        this.openIDDiscoveryService = openIDDiscoveryService;
        this.domain = domain;
    }

    @Override
    public String assertionType() {
        return ClientAuthenticationMethod.JWT_BEARER;
    }

    @Override
    public Maybe<Client> validate(String assertion, String basePath, String clientIdHint) {
        return parseJwt(assertion).flatMap(jwt -> dispatch(jwt, basePath));
    }

    private Maybe<Client> dispatch(JWT jwt, String basePath) {
        final String iss;
        final String sub;
        try {
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            iss = claims.getIssuer();
            sub = claims.getSubject();
        } catch (ParseException ex) {
            return Maybe.error(NOT_VALID);
        }

        // SPIFFE JWT-SVID on the jwt-bearer assertion type is a client misconfiguration.
        if (SpiffeJwtSvidValidator.isSpiffeId(sub)) {
            return Maybe.error(new InvalidClientException(
                    "SPIFFE JWT-SVID must be sent with client_assertion_type="
                            + ClientAuthenticationMethod.JWT_SPIFFE));
        }

        // Strict RFC 7523: iss MUST equal sub for private_key_jwt / client_secret_jwt.
        if (iss == null || !iss.equals(sub)) {
            return Maybe.error(NOT_VALID);
        }

        return validateJWT(jwt, basePath)
                .flatMap(validated -> {
                    if (JWSAlgorithm.Family.HMAC_SHA.contains(validated.getHeader().getAlgorithm())) {
                        return validateSignatureWithHMAC(validated);
                    }
                    return validateSignatureWithPublicKey(validated);
                });
    }

    /**
     * Ensures that all claims required by RFC 7523 are present and valid.
     * See <a href="https://tools.ietf.org/html/rfc7523#section-3">RFC 7523 §3</a>.
     */
    private Maybe<JWT> validateJWT(JWT jwt, String basePath) {
        final String iss;
        final String sub;
        final List<String> aud;
        final Date exp;
        try {
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            iss = claims.getIssuer();
            sub = claims.getSubject();
            aud = claims.getAudience();
            exp = claims.getExpirationTime();
        } catch (ParseException ex) {
            return Maybe.error(NOT_VALID);
        }

        if (iss == null || iss.isEmpty() || sub == null || sub.isEmpty()
                || aud == null || aud.isEmpty() || exp == null) {
            return Maybe.error(NOT_VALID);
        }

        if (exp.before(Date.from(Instant.now()))) {
            return Maybe.error(new InvalidClientException("assertion has expired"));
        }

        OpenIDProviderMetadata discovery = openIDDiscoveryService.getConfiguration(basePath);
        if (discovery == null || discovery.getTokenEndpoint() == null) {
            return Maybe.error(new ServerErrorException("Unable to retrieve discovery token endpoint."));
        }

        // OIDC: "The Audience SHOULD be the URL of the Authorization Server's Token Endpoint."
        // PAR allows Issuer, Token endpoint, or PAR endpoint.
        boolean audMatches = aud.stream().anyMatch(discovery.getTokenEndpoint()::equals)
                || (discovery.getIssuer() != null && aud.stream().anyMatch(discovery.getIssuer()::equals))
                || (discovery.getParEndpoint() != null && aud.stream().anyMatch(discovery.getParEndpoint()::equals))
                || (discovery.getBackchannelAuthenticationEndpoint() != null
                        && aud.stream().anyMatch(discovery.getBackchannelAuthenticationEndpoint()::equals));
        if (!audMatches) {
            return Maybe.error(NOT_VALID);
        }

        if (this.domain.usePlainFapiProfile()
                && !isSignAlgCompliantWithFapi(jwt.getHeader().getAlgorithm().getName())) {
            return Maybe.error(new InvalidClientException("JWT Assertion must be signed with PS256"));
        }

        return Maybe.just(jwt);
    }

    private Maybe<Client> validateSignatureWithPublicKey(JWT jwt) {
        final SignedJWT signedJWT;
        final String clientId;
        try {
            signedJWT = (SignedJWT) jwt;
            clientId = jwt.getJWTClaimsSet().getSubject();
        } catch (ClassCastException | ParseException ex) {
            log.error(ex.getMessage(), ex);
            return Maybe.error(NOT_VALID);
        } catch (IllegalArgumentException ex) {
            return Maybe.error(new InvalidClientException(ex.getMessage()));
        }

        return clientLookupService.findByClientId(clientId)
                .switchIfEmpty(Maybe.error(new InvalidClientException("Missing or invalid client")))
                .flatMap(client -> {
                    if (client.getTokenEndpointAuthMethod() != null &&
                            !ClientAuthenticationMethod.PRIVATE_KEY_JWT.equalsIgnoreCase(client.getTokenEndpointAuthMethod())) {
                        return Maybe.error(new InvalidClientException(
                                "Invalid client: missing or unsupported authentication method"));
                    }
                    return getClientJwkSet(client)
                            .switchIfEmpty(Maybe.error(new InvalidClientException("No jwk keys available on client")))
                            .flatMap(jwkSet -> jwkService.getKey(jwkSet, signedJWT.getHeader().getKeyID()))
                            .switchIfEmpty(Maybe.error(new InvalidClientException(
                                    "Unable to validate client, no matching key.")))
                            .flatMap(jwk -> {
                                if (jwsService.isValidSignature(signedJWT, jwk)) {
                                    return Maybe.just(client);
                                }
                                return Maybe.error(unableToValidateClient());
                            });
                });
    }

    private Maybe<Client> validateSignatureWithHMAC(JWT jwt) {
        Algorithm algorithm = jwt.getHeader().getAlgorithm();
        if (!(algorithm instanceof JWSAlgorithm)) {
            return Maybe.error(unableToValidateClient());
        }
        JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(algorithm.getName());
        if (jwsAlgorithm != JWSAlgorithm.HS256 && jwsAlgorithm != JWSAlgorithm.HS384 && jwsAlgorithm != JWSAlgorithm.HS512) {
            return Maybe.error(unableToValidateClient());
        }

        final SignedJWT signedJWT;
        final String clientId;
        try {
            signedJWT = (SignedJWT) jwt;
            clientId = jwt.getJWTClaimsSet().getSubject();
        } catch (ClassCastException | ParseException ex) {
            log.error(ex.getMessage(), ex);
            return Maybe.error(NOT_VALID);
        } catch (IllegalArgumentException ex) {
            return Maybe.error(new InvalidClientException(ex.getMessage()));
        }

        return clientLookupService.findByClientId(clientId)
                .switchIfEmpty(Maybe.error(new InvalidClientException("Missing or invalid client")))
                .flatMap(client -> {
                    if (client.getTokenEndpointAuthMethod() != null &&
                            !ClientAuthenticationMethod.CLIENT_SECRET_JWT.equalsIgnoreCase(client.getTokenEndpointAuthMethod())) {
                        return Maybe.error(new InvalidClientException(
                                "Invalid client: missing or unsupported authentication method"));
                    }
                    try {
                        if (verifyJws(client, signedJWT)) {
                            return Maybe.just(client);
                        }
                        return Maybe.error(new InvalidClientException(
                                "Invalid client: JWT signature verification failed"));
                    } catch (JOSEException josee) {
                        log.error("Error validating signature: {}", josee.getMessage(), josee);
                        return Maybe.error(new InvalidClientException(
                                "Error validating client JWT signature", josee));
                    }
                });
    }

    private static boolean verifyJws(Client client, SignedJWT signedJWT) throws JOSEException {
        if (!isEmpty(client.getClientSecrets())) {
            for (ClientSecret clientSecret : client.getClientSecrets()) {
                // client_secret_jwt cannot use hashed secrets — verify against the raw value.
                JWSVerifier verifier = new MACVerifier(clientSecret.getSecret());
                if (signedJWT.verify(verifier)) {
                    return true;
                }
            }
            return false;
        }
        // Prior to 4.2, the client secret was stored directly in clientSecret (unhashed).
        JWSVerifier verifier = new MACVerifier(client.getClientSecret());
        return signedJWT.verify(verifier);
    }

    private Maybe<JWKSet> getClientJwkSet(Client client) {
        if (client.getJwksUri() != null && !client.getJwksUri().trim().isEmpty()) {
            return jwkService.getKeys(client.getJwksUri());
        } else if (client.getJwks() != null) {
            return Maybe.just(client.getJwks());
        }
        return Maybe.empty();
    }
}
