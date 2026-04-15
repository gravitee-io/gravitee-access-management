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
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.impl.SecretService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeSource;
import io.reactivex.rxjava3.functions.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import io.gravitee.am.model.application.AgentType;

import static io.gravitee.am.common.oidc.ClientAuthenticationMethod.JWT_BEARER;
import static io.gravitee.am.common.oidc.ClientAuthenticationMethod.WORKLOAD_JWT;
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

    @Override
    public Maybe<Client> assertClient(String assertionType, String assertion, String basePath) {

        InvalidClientException unsupportedAssertionType = new InvalidClientException("Unknown or unsupported assertion_type");

        if (assertionType == null || assertionType.isEmpty()) {
            return Maybe.error(unsupportedAssertionType);
        }

        if (JWT_BEARER.equals(assertionType)) {
            return this.validateJWT(assertion, basePath)
                    .flatMap((Function<JWT, MaybeSource<Client>>) jwt -> {
                        // Handle client_secret_key client authentication
                        if (JWSAlgorithm.Family.HMAC_SHA.contains(jwt.getHeader().getAlgorithm())) {
                            return validateSignatureWithHMAC(jwt);
                        } else {
                            // Handle private_key_jwt client authentication
                            return validateSignatureWithPublicKey(jwt);
                        }
                    });
        }

        if (WORKLOAD_JWT.equals(assertionType)) {
            return this.validateWorkloadJWT(assertion, basePath);
        }

        return Maybe.error(unsupportedAssertionType);
    }

    /**
     * This method will parse the JWT bearer then ensure that all requested claims are set as required
     * <a href="https://tools.ietf.org/html/rfc7523#section-3">here</a>
     * @param assertion jwt as string value.
     * @return
     */
    private Maybe<JWT> validateJWT(String assertion, String basePath) {
        try {
            JWT jwt = JWTParser.parse(assertion);

            String iss = jwt.getJWTClaimsSet().getIssuer();
            String sub = jwt.getJWTClaimsSet().getSubject();
            List<String> aud = jwt.getJWTClaimsSet().getAudience();
            Date exp = jwt.getJWTClaimsSet().getExpirationTime();

            if (iss == null || iss.isEmpty() || sub == null || sub.isEmpty() || aud == null || aud.isEmpty() || exp == null) {
                return Maybe.error(NOT_VALID);
            }

            if (exp.before(Date.from(Instant.now()))) {
                return Maybe.error(new InvalidClientException("assertion has expired"));
            }

            //Check audience, here we expect to have absolute token endpoint path.
            OpenIDProviderMetadata discovery = openIDDiscoveryService.getConfiguration(basePath);
            if (discovery == null || discovery.getTokenEndpoint() == null) {
                return Maybe.error(new ServerErrorException("Unable to retrieve discovery token endpoint."));
            }

            // OIDC specifies that "The Audience SHOULD be the URL of the Authorization Server's Token Endpoint."
            // https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication
            // BUT the PAR specification specify the usage of the Issuer value, Token endpoint or PAR endpoint.
            // https://tools.ietf.org/id/draft-lodderstedt-oauth-par-00.html#pushed-authorization-request-endpoint
            if (aud.stream().filter(discovery.getTokenEndpoint()::equals).count() == 0 &&
                    (discovery.getIssuer() != null && aud.stream().filter(discovery.getIssuer()::equals).count() == 0) &&
                    (discovery.getParEndpoint() != null && aud.stream().filter(discovery.getParEndpoint()::equals).count() == 0) &&
                    (discovery.getBackchannelAuthenticationEndpoint() != null && aud.stream().filter(discovery.getBackchannelAuthenticationEndpoint()::equals).count() == 0)) {
                return Maybe.error(NOT_VALID);
            }

            if (this.domain.usePlainFapiProfile() && !isSignAlgCompliantWithFapi(jwt.getHeader().getAlgorithm().getName())) {
                return Maybe.error(new InvalidClientException("JWT Assertion must be signed with PS256"));
            }

            return Maybe.just(jwt);
        } catch (ParseException pe) {
            return Maybe.error(NOT_VALID);
        }
    }

    private Maybe<Client> validateSignatureWithPublicKey(JWT jwt) {
        try {
            String clientId = jwt.getJWTClaimsSet().getSubject();
            SignedJWT signedJWT = (SignedJWT) jwt;

            return this.clientLookupService.findByClientId(clientId)
                    .switchIfEmpty(Maybe.error(new InvalidClientException("Missing or invalid client")))
                    .flatMap(client -> {
                        if (client.getTokenEndpointAuthMethod() == null ||
                                ClientAuthenticationMethod.PRIVATE_KEY_JWT.equalsIgnoreCase(client.getTokenEndpointAuthMethod())) {
                            return this.getClientJwkSet(client)
                                    .switchIfEmpty(Maybe.error(new InvalidClientException("No jwk keys available on client")))
                                    .flatMap(jwkSet -> jwkService.getKey(jwkSet, signedJWT.getHeader().getKeyID()))
                                    .switchIfEmpty(Maybe.error(new InvalidClientException("Unable to validate client, no matching key.")))
                                    .flatMap(jwk -> {
                                        if (jwsService.isValidSignature(signedJWT, jwk)) {
                                            return Maybe.just(client);
                                        }
                                        return Maybe.error(unableToValidateClientException());
                                    });
                        } else {
                            return Maybe.error(new InvalidClientException("Invalid client: missing or unsupported authentication method"));
                        }
                    });
        } catch (ClassCastException | ParseException ex) {
            log.error(ex.getMessage(), ex);
            return Maybe.error(NOT_VALID);
        } catch (IllegalArgumentException ex) {
            return Maybe.error(new InvalidClientException(ex.getMessage()));
        }
    }

    private Maybe<Client> validateSignatureWithHMAC(JWT jwt) {
        try {
            Algorithm algorithm = jwt.getHeader().getAlgorithm();

            if (algorithm instanceof JWSAlgorithm) {
                JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jwt.getHeader().getAlgorithm().getName());
                if (jwsAlgorithm != JWSAlgorithm.HS256 && jwsAlgorithm != JWSAlgorithm.HS384 && jwsAlgorithm != JWSAlgorithm.HS512) {
                    return Maybe.error(unableToValidateClientException());
                }
            } else {
                return Maybe.error(unableToValidateClientException());
            }

            String clientId = jwt.getJWTClaimsSet().getSubject();
            SignedJWT signedJWT = (SignedJWT) jwt;


            return this.clientLookupService.findByClientId(clientId)
                    .switchIfEmpty(Maybe.error(new InvalidClientException("Missing or invalid client")))
                    .flatMap(client -> {
                        try {
                            // Ensure to validate JWT using client_secret_key only if client is authorized to use this auth method
                            if (client.getTokenEndpointAuthMethod() == null ||
                                    ClientAuthenticationMethod.CLIENT_SECRET_JWT.equalsIgnoreCase(client.getTokenEndpointAuthMethod())) {

                                if (verifyJws(client, signedJWT)) {
                                    return Maybe.just(client);
                                } else {
                                    return Maybe.error(new InvalidClientException("Invalid client: JWT signature verification failed"));
                                }
                            } else {
                                return Maybe.error(new InvalidClientException("Invalid client: missing or unsupported authentication method"));
                            }
                        } catch (JOSEException josee) {
                            log.error("Error validating signature: {}", josee.getMessage(), josee);
                            return Maybe.error(new InvalidClientException("Error validating client JWT signature", josee));
                        }
                    });
        } catch (ClassCastException | ParseException ex) {
            log.error(ex.getMessage(), ex);
            return Maybe.error(NOT_VALID);
        } catch (IllegalArgumentException ex) {
            return Maybe.error(new InvalidClientException(ex.getMessage()));
        }
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
            // If none of the secrets verify the JWT, return false.
            return false;
        } else {
            // Prior to 4.2, the client secret was not hashed and directly stored in the clientSecret attribute.
            JWSVerifier verifier = new MACVerifier(client.getClientSecret());
            return signedJWT.verify(verifier);
        }
    }

    /**
     * Validate a workload-jwt assertion for blueprint agent instances.
     * <p>
     * The JWT {@code iss} identifies the blueprint application (client_id) and
     * {@code sub} identifies the agent instance. The signature is verified
     * against the blueprint's agent JWKS. On success, a cloned Client is
     * returned with the {@code clientId} set to the agent instance ID and
     * {@code blueprintClientId} set to the original blueprint client_id.
     */
    private Maybe<Client> validateWorkloadJWT(String assertion, String basePath) {
        try {
            JWT jwt = JWTParser.parse(assertion);
            SignedJWT signedJWT = (SignedJWT) jwt;

            String iss = jwt.getJWTClaimsSet().getIssuer();
            String sub = jwt.getJWTClaimsSet().getSubject();
            List<String> aud = jwt.getJWTClaimsSet().getAudience();
            Date exp = jwt.getJWTClaimsSet().getExpirationTime();

            if (iss == null || iss.isEmpty() || sub == null || sub.isEmpty()
                    || aud == null || aud.isEmpty() || exp == null) {
                return Maybe.error(NOT_VALID);
            }

            if (exp.before(Date.from(Instant.now()))) {
                return Maybe.error(new InvalidClientException("assertion has expired"));
            }

            // Validate audience
            OpenIDProviderMetadata discovery = openIDDiscoveryService.getConfiguration(basePath);
            if (discovery == null || discovery.getTokenEndpoint() == null) {
                return Maybe.error(new ServerErrorException("Unable to retrieve discovery token endpoint."));
            }
            if (aud.stream().noneMatch(discovery.getTokenEndpoint()::equals)
                    && (discovery.getIssuer() == null || aud.stream().noneMatch(discovery.getIssuer()::equals))) {
                return Maybe.error(NOT_VALID);
            }

            // Look up blueprint client by iss
            return this.clientSyncService.findByClientId(iss)
                    .switchIfEmpty(Maybe.error(new InvalidClientException("Unknown blueprint application")))
                    .flatMap(blueprint -> {
                        if (!blueprint.isAgentIdentityMode()) {
                            return Maybe.error(new InvalidClientException("Application is not a blueprint agent"));
                        }
                        AgentType agentType = blueprint.getAgentType();
                        if (agentType != AgentType.AUTONOMOUS && agentType != AgentType.HOSTED_DELEGATED) {
                            return Maybe.error(new InvalidClientException("Workload-jwt is not supported for agent type: " + agentType));
                        }

                        JWKSet agentJwks = blueprint.getAgentJwks();
                        if (agentJwks == null || agentJwks.getKeys() == null || agentJwks.getKeys().isEmpty()) {
                            return Maybe.error(new InvalidClientException("No agent JWKS configured on blueprint"));
                        }

                        return jwkService.getKey(agentJwks, signedJWT.getHeader().getKeyID())
                                .switchIfEmpty(Maybe.error(new InvalidClientException("No matching key in blueprint agent JWKS")))
                                .flatMap(jwk -> {
                                    if (!jwsService.isValidSignature(signedJWT, jwk)) {
                                        return Maybe.error(unableToValidateClientException());
                                    }
                                    // Clone blueprint — keep clientId as the blueprint's so auth code
                                    // lookups and other grant validations still match. The agent
                                    // instance ID is stored separately for token sub override.
                                    Client agentClient = new Client(blueprint);
                                    agentClient.setBlueprintClientId(blueprint.getClientId());
                                    agentClient.setAgentInstanceId(sub);
                                    return Maybe.just(agentClient);
                                });
                    });
        } catch (ClassCastException | ParseException ex) {
            log.error("Failed to parse workload-jwt assertion: {}", ex.getMessage(), ex);
            return Maybe.error(NOT_VALID);
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

    private InvalidClientException unableToValidateClientException() {
        return new InvalidClientException("Unable to validate client, assertion signature is not valid.");
    }
}
