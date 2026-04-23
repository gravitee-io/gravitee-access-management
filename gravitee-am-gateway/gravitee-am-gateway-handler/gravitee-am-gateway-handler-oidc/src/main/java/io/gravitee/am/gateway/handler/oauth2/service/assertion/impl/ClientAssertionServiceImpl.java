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
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.application.AgentType;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.impl.SecretService;
import io.gravitee.am.service.reporter.builder.AgentAuditBuilder;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static io.gravitee.am.common.oidc.ClientAuthenticationMethod.JWT_BEARER;
import static io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils.isSignAlgCompliantWithFapi;
import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * Client assertion as described for <a href="https://tools.ietf.org/html/rfc7521#section-4.2">oauth2 assertion framework</a>
 * and <a href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">openid client authentication specs</a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Slf4j
public class ClientAssertionServiceImpl implements ClientAssertionService {

    private static final InvalidClientException NOT_VALID = new InvalidClientException("assertion is not valid");

    @Autowired
    @Qualifier("complexClientLookupService")
    private ClientLookupService clientLookupService;

    @Autowired
    private JWKService jwkService;

    @Autowired
    private JWSService jwsService;

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Autowired
    private Domain domain;

    @Autowired
    private SecretService appSecretService;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<Client> assertClient(String assertionType, String assertion, String basePath) {
        if (assertionType == null || assertionType.isEmpty()) {
            return Maybe.error(unsupportedAssertionType());
        }
        if (!JWT_BEARER.equals(assertionType)) {
            return Maybe.error(unsupportedAssertionType());
        }

        return parseJwt(assertion)
                .flatMap(jwt -> dispatchJwtBearer(jwt, basePath));
    }

    private static Maybe<JWT> parseJwt(String assertion) {
        return Maybe.defer(() -> {
            try {
                return Maybe.just(JWTParser.parse(assertion));
            } catch (ParseException pe) {
                return Maybe.error(NOT_VALID);
            }
        });
    }

    /**
     * Dispatch an {@code urn:ietf:params:oauth:client-assertion-type:jwt-bearer}
     * assertion. Standard RFC 7523 assertions have {@code iss == sub == client_id}
     * and are handled by the private_key_jwt / client_secret_jwt path. Blueprint
     * agent instance assertions have a distinct shape — either {@code iss} is a
     * URI resolvable via CIMD, or {@code iss != sub} (blueprint vs. agent
     * instance) — and route through the agent assertion path.
     */
    private Maybe<Client> dispatchJwtBearer(JWT jwt, String basePath) {
        return Maybe.defer(() -> {
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            String iss = claims.getIssuer();
            String sub = claims.getSubject();

            if (isAgentAssertionShape(iss, sub)) {
                return validateAgentAssertion(jwt, basePath);
            }

            return validateJWT(jwt, basePath)
                    .flatMap(validated -> {
                        if (JWSAlgorithm.Family.HMAC_SHA.contains(validated.getHeader().getAlgorithm())) {
                            return validateSignatureWithHMAC(validated);
                        }
                        return validateSignatureWithPublicKey(validated);
                    });
        });
    }

    private static boolean isAgentAssertionShape(String iss, String sub) {
        if (iss == null || sub == null) {
            return false;
        }
        return isUri(iss) || !iss.equals(sub);
    }

    private static boolean isUri(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }

    /**
     * Ensures that all claims required by RFC 7523 are present and valid.
     * See <a href="https://tools.ietf.org/html/rfc7523#section-3">RFC 7523 §3</a>.
     */
    private Maybe<JWT> validateJWT(JWT jwt, String basePath) {
        return Maybe.defer(() -> {
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            String iss = claims.getIssuer();
            String sub = claims.getSubject();
            List<String> aud = claims.getAudience();
            Date exp = claims.getExpirationTime();

            if (iss == null || iss.isEmpty() || sub == null || sub.isEmpty() || aud == null || aud.isEmpty() || exp == null) {
                return Maybe.error(NOT_VALID);
            }

            if (exp.before(Date.from(Instant.now()))) {
                return Maybe.error(new InvalidClientException("assertion has expired"));
            }

            OpenIDProviderMetadata discovery = openIDDiscoveryService.getConfiguration(basePath);
            if (discovery == null || discovery.getTokenEndpoint() == null) {
                return Maybe.error(new ServerErrorException("Unable to retrieve discovery token endpoint."));
            }

            // OIDC specifies that "The Audience SHOULD be the URL of the Authorization Server's Token Endpoint."
            // https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication
            // BUT the PAR specification specify the usage of the Issuer value, Token endpoint or PAR endpoint.
            // https://tools.ietf.org/id/draft-lodderstedt-oauth-par-00.html#pushed-authorization-request-endpoint
            boolean audMatches = aud.stream().anyMatch(discovery.getTokenEndpoint()::equals)
                    || (discovery.getIssuer() != null && aud.stream().anyMatch(discovery.getIssuer()::equals))
                    || (discovery.getParEndpoint() != null && aud.stream().anyMatch(discovery.getParEndpoint()::equals))
                    || (discovery.getBackchannelAuthenticationEndpoint() != null
                            && aud.stream().anyMatch(discovery.getBackchannelAuthenticationEndpoint()::equals));
            if (!audMatches) {
                return Maybe.error(NOT_VALID);
            }

            if (this.domain.usePlainFapiProfile() && !isSignAlgCompliantWithFapi(jwt.getHeader().getAlgorithm().getName())) {
                return Maybe.error(new InvalidClientException("JWT Assertion must be signed with PS256"));
            }

            return Maybe.just(jwt);
        });
    }

    private Maybe<Client> validateSignatureWithPublicKey(JWT jwt) {
        return Maybe.defer(() -> {
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
                            return Maybe.error(new InvalidClientException("Invalid client: missing or unsupported authentication method"));
                        }
                        return getClientJwkSet(client)
                                .switchIfEmpty(Maybe.error(new InvalidClientException("No jwk keys available on client")))
                                .flatMap(jwkSet -> jwkService.getKey(jwkSet, signedJWT.getHeader().getKeyID()))
                                .switchIfEmpty(Maybe.error(new InvalidClientException("Unable to validate client, no matching key.")))
                                .flatMap(jwk -> {
                                    if (jwsService.isValidSignature(signedJWT, jwk)) {
                                        return Maybe.just(client);
                                    }
                                    return Maybe.error(unableToValidateClientException());
                                });
                    });
        });
    }

    private Maybe<Client> validateSignatureWithHMAC(JWT jwt) {
        return Maybe.defer(() -> {
            Algorithm algorithm = jwt.getHeader().getAlgorithm();
            if (!(algorithm instanceof JWSAlgorithm)) {
                return Maybe.error(unableToValidateClientException());
            }
            JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(algorithm.getName());
            if (jwsAlgorithm != JWSAlgorithm.HS256 && jwsAlgorithm != JWSAlgorithm.HS384 && jwsAlgorithm != JWSAlgorithm.HS512) {
                return Maybe.error(unableToValidateClientException());
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
                        // Ensure to validate JWT using client_secret_key only if client is authorized to use this auth method
                        if (client.getTokenEndpointAuthMethod() != null &&
                                !ClientAuthenticationMethod.CLIENT_SECRET_JWT.equalsIgnoreCase(client.getTokenEndpointAuthMethod())) {
                            return Maybe.error(new InvalidClientException("Invalid client: missing or unsupported authentication method"));
                        }
                        try {
                            if (verifyJws(client, signedJWT)) {
                                return Maybe.just(client);
                            }
                            return Maybe.error(new InvalidClientException("Invalid client: JWT signature verification failed"));
                        } catch (JOSEException josee) {
                            log.error("Error validating signature: {}", josee.getMessage(), josee);
                            return Maybe.error(new InvalidClientException("Error validating client JWT signature", josee));
                        }
                    });
        });
    }

    private static boolean verifyJws(Client client, SignedJWT signedJWT) throws JOSEException {
        if (!isEmpty(client.getClientSecrets())) {
            for (ClientSecret clientSecret : client.getClientSecrets()) {
                // No need to decode the secret, as for client_secret_jwt
                // the client can't generate a secret using a hash algorithm.
                JWSVerifier verifier = new MACVerifier(clientSecret.getSecret());
                if (signedJWT.verify(verifier)) {
                    return true;
                }
            }
            return false;
        }
        // Prior to 4.2, the client secret was not hashed and directly stored in the clientSecret attribute.
        JWSVerifier verifier = new MACVerifier(client.getClientSecret());
        return signedJWT.verify(verifier);
    }

    /**
     * Validate a blueprint-agent instance assertion carried as a
     * {@code jwt-bearer} client assertion.
     * <p>
     * The JWT {@code iss} identifies the blueprint application (client_id, or a
     * URL-shaped client_id that resolves via master's CIMD-aware client lookup)
     * and {@code sub} identifies the agent instance. The signature is verified
     * against the blueprint's JWKS.
     */
    private Maybe<Client> validateAgentAssertion(JWT jwt, String basePath) {
        return Maybe.defer(() -> {
            if (!(jwt instanceof SignedJWT signedJWT)) {
                return Maybe.error(NOT_VALID);
            }

            final JWTClaimsSet claims;
            try {
                claims = jwt.getJWTClaimsSet();
            } catch (ParseException ex) {
                log.debug("Failed to parse agent jwt-bearer assertion claims: {}", ex.getMessage());
                return Maybe.error(NOT_VALID);
            }

            String iss = claims.getIssuer();
            String sub = claims.getSubject();
            List<String> aud = claims.getAudience();
            Date exp = claims.getExpirationTime();

            if (iss == null || iss.isEmpty() || sub == null || sub.isEmpty()
                    || aud == null || aud.isEmpty() || exp == null) {
                return Maybe.error(NOT_VALID);
            }

            if (exp.toInstant().isBefore(Instant.now())) {
                return Maybe.error(new InvalidClientException("assertion has expired"));
            }

            // Per the Agent Identity proposal, the audience for a workload jwt-bearer
            // assertion MUST be the AM token endpoint — not the base issuer or PAR
            // endpoint. Discovery + token endpoint are always available (derived from
            // basePath), so no null guard is required.
            String tokenEndpoint = openIDDiscoveryService.getConfiguration(basePath).getTokenEndpoint();
            if (aud.stream().noneMatch(tokenEndpoint::equals)) {
                return Maybe.error(NOT_VALID);
            }

            String kid = signedJWT.getHeader().getKeyID();
            String jti = claims.getJWTID();

            // Look up blueprint. For URL-shaped iss the CIMD-aware lookup service
            // resolves the synthesized client via the domain's CIMD metadata flow.
            return clientLookupService.findByClientId(iss)
                    .switchIfEmpty(Maybe.error(new InvalidClientException("Unknown blueprint application")))
                    .flatMap(blueprint -> verifyAgentBlueprint(blueprint, signedJWT, kid)
                            .doOnError(err -> {
                                if (err instanceof InvalidClientException
                                        && err.getMessage() != null
                                        && err.getMessage().startsWith("Unable to validate client")) {
                                    reportAgentAuth(iss, sub, kid, jti, blueprint, "INVALID_SIGNATURE");
                                }
                            }))
                    .map(blueprint -> buildAgentClient(blueprint, sub))
                    .doOnSuccess(agentClient -> reportAgentAuth(iss, sub, kid, jti, agentClient, null));
        });
    }

    private Maybe<Client> verifyAgentBlueprint(Client blueprint, SignedJWT signedJWT, String kid) {
        if (!blueprint.isAgentIdentityMode()) {
            return Maybe.error(new InvalidClientException("Application is not a blueprint agent"));
        }
        AgentType agentType = blueprint.getAgentType();
        if (agentType != AgentType.AUTONOMOUS && agentType != AgentType.HOSTED_DELEGATED) {
            return Maybe.error(new InvalidClientException("Agent jwt-bearer assertion is not supported for agent type: " + agentType));
        }
        String authMethod = blueprint.getTokenEndpointAuthMethod();
        if (!ClientAuthenticationMethod.PRIVATE_KEY_JWT.equals(authMethod)
                && !ClientAuthenticationMethod.CLIENT_SECRET_JWT.equals(authMethod)) {
            return Maybe.error(new InvalidClientException(
                    "Blueprint is not configured for jwt-bearer client assertions"));
        }

        JWKSet agentJwks = blueprint.getJwks();
        if (agentJwks == null || agentJwks.getKeys() == null || agentJwks.getKeys().isEmpty()) {
            return Maybe.error(new InvalidClientException("No agent JWKS available on blueprint"));
        }

        return jwkService.getKey(agentJwks, kid)
                .switchIfEmpty(Maybe.error(new InvalidClientException("No matching key found for kid: " + kid)))
                .flatMap(jwk -> {
                    if (!jwsService.isValidSignature(signedJWT, jwk)) {
                        return Maybe.error(unableToValidateClientException());
                    }
                    return Maybe.just(blueprint);
                });
    }

    private static Client buildAgentClient(Client blueprint, String agentInstanceId) {
        Client agentClient = new Client(blueprint);
        agentClient.setAgentInstanceId(agentInstanceId);
        return agentClient;
    }

    private void reportAgentAuth(String iss, String sub, String kid, String jti,
                                 Client blueprint, String failureReason) {
        try {
            var builder = AuditBuilder.builder(AgentAuditBuilder.class);
            if (blueprint.getDomain() != null) {
                builder.reference(Reference.domain(blueprint.getDomain()));
            }
            builder
                    .blueprintId(blueprint.getClientId())
                    .blueprintName(blueprint.getClientName())
                    .agentInstanceId(sub)
                    .agentType(blueprint.getAgentType() != null ? blueprint.getAgentType().name() : null)
                    .assertionKid(kid)
                    .assertionIss(iss)
                    .assertionJti(jti);

            if (failureReason != null) {
                builder.throwable(new InvalidClientException(failureReason));
            }

            auditService.report(builder);
        } catch (Exception e) {
            log.warn("Failed to report agent authentication audit", e);
        }
    }

    private Maybe<JWKSet> getClientJwkSet(Client client) {
        if (client.getJwksUri() != null && !client.getJwksUri().trim().isEmpty()) {
            return jwkService.getKeys(client.getJwksUri());
        } else if (client.getJwks() != null) {
            return Maybe.just(client.getJwks());
        }
        return Maybe.empty();
    }

    private static InvalidClientException unsupportedAssertionType() {
        return new InvalidClientException("Unknown or unsupported assertion_type");
    }

    private InvalidClientException unableToValidateClientException() {
        return new InvalidClientException("Unable to validate client, assertion signature is not valid.");
    }
}
