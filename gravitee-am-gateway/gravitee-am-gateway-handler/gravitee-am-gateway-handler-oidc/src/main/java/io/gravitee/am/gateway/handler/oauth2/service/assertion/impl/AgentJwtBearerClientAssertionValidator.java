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

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionValidator;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.AgentType;
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

/**
 * Validates a blueprint-agent instance assertion carried as a dedicated
 * {@code agent-jwt-bearer} client assertion. The JWT {@code iss} identifies
 * the blueprint application (client_id, or a URL-shaped client_id resolved via
 * the CIMD-aware client lookup), {@code sub} identifies the agent instance,
 * and the signature is verified against the blueprint's JWKS.
 */
@CustomLog
public class AgentJwtBearerClientAssertionValidator implements ClientAssertionValidator {

    private final ClientLookupService clientLookupService;
    private final JWKService jwkService;
    private final JWSService jwsService;
    private final OpenIDDiscoveryService openIDDiscoveryService;
    private final Domain domain;

    public AgentJwtBearerClientAssertionValidator(ClientLookupService clientLookupService,
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
        return ClientAuthenticationMethod.AGENT_JWT_BEARER;
    }

    @Override
    public Maybe<Client> validate(String assertion, String basePath, String clientIdHint) {
        return parseJwt(assertion).flatMap(jwt -> validateAgentAssertion(jwt, basePath));
    }

    private Maybe<Client> validateAgentAssertion(JWT jwt, String basePath) {
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

        if (this.domain.usePlainFapiProfile()
                && !isSignAlgCompliantWithFapi(signedJWT.getHeader().getAlgorithm().getName())) {
            return Maybe.error(new InvalidClientException("JWT Assertion must be signed with PS256"));
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

        // For URL-shaped iss the CIMD-aware lookup resolves a synthesized client via
        // the domain's CIMD metadata flow.
        return clientLookupService.findByClientId(iss)
                .switchIfEmpty(Maybe.error(new InvalidClientException("Unknown blueprint application")))
                .flatMap(blueprint -> verifyAgentBlueprint(blueprint, signedJWT, kid))
                .map(blueprint -> buildAgentClient(blueprint, sub));
    }

    private Maybe<Client> verifyAgentBlueprint(Client blueprint, SignedJWT signedJWT, String kid) {
        if (!blueprint.isAgentApplication()) {
            return Maybe.error(new InvalidClientException("Application is not an agent application"));
        }
        AgentType agentType = blueprint.getAgentType();
        if (agentType != AgentType.AUTONOMOUS && agentType != AgentType.HOSTED_DELEGATED) {
            return Maybe.error(new InvalidClientException(
                    "Agent jwt-bearer assertion is not supported for agent type: " + agentType));
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
                        return Maybe.error(unableToValidateClient());
                    }
                    return Maybe.just(blueprint);
                });
    }

    private static Client buildAgentClient(Client blueprint, String agentInstanceId) {
        Client agentClient = new Client(blueprint);
        agentClient.setAgentInstanceId(agentInstanceId);
        return agentClient;
    }
}
