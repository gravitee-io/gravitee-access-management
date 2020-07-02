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
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.functions.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Client assertion as described for <a href="https://tools.ietf.org/html/rfc7521#section-4.2">oauth2 assertion framework</a>
 * and <a href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">openid client authentication specs</a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class ClientAssertionServiceImpl implements ClientAssertionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAssertionServiceImpl.class);

    private static final String JWT_BEARER = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final InvalidClientException NOT_VALID = new InvalidClientException("assertion is not valid");

    @Autowired
    private ClientSyncService clientSyncService;

    @Autowired
    private JWKService jwkService;

    @Autowired
    private JWSService jwsService;

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Override
    public Maybe<Client> assertClient(String assertionType, String assertion, String basePath) {

        InvalidClientException unsupportedAssertionType = new InvalidClientException("Unknown or unsupported assertion_type");

        if (assertionType == null || assertionType.isEmpty()) {
            return Maybe.error(unsupportedAssertionType);
        }

        if (JWT_BEARER.equals(assertionType)) {
            return this.validateJWT(assertion, basePath)
                    .flatMap(new Function<JWT, MaybeSource<Client>>() {
                        @Override
                        public MaybeSource<Client> apply(JWT jwt) throws Exception {
                            // Handle client_secret_key client authentication
                            if (JWSAlgorithm.Family.HMAC_SHA.contains(jwt.getHeader().getAlgorithm())) {
                                return validateSignatureWithHMAC(jwt);
                            } else {
                                // Handle private_key_jwt client authentication
                                return validateSignatureWithPublicKey(jwt);
                            }
                        }
                    });
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

            if  (iss == null || iss.isEmpty() || sub == null || sub.isEmpty() || aud == null || aud.isEmpty() || exp == null) {
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

            if (aud.stream().filter(discovery.getTokenEndpoint()::equals).count()==0) {
                return Maybe.error(NOT_VALID);
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

            return this.clientSyncService.findByClientId(clientId)
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
                                        return Maybe.error(new InvalidClientException("Unable to validate client, assertion signature is not valid."));
                                    });
                        } else {
                            return Maybe.error(new InvalidClientException("Invalid client: missing or unsupported authentication method"));
                        }
                    });
        } catch (ClassCastException | ParseException ex) {
            LOGGER.error(ex.getMessage(),ex);
            return Maybe.error(NOT_VALID);
        }
        catch (IllegalArgumentException ex) {
            return Maybe.error(new InvalidClientException(ex.getMessage()));
        }
    }

    private Maybe<Client> validateSignatureWithHMAC(JWT jwt) {
        try {
            Algorithm algorithm = jwt.getHeader().getAlgorithm();

            if (algorithm instanceof JWSAlgorithm) {
                JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jwt.getHeader().getAlgorithm().getName());
                if (jwsAlgorithm != JWSAlgorithm.HS256 && jwsAlgorithm != JWSAlgorithm.HS384 && jwsAlgorithm != JWSAlgorithm.HS512) {
                    return Maybe.error(new InvalidClientException("Unable to validate client, assertion signature is not valid."));
                }
            } else {
                return Maybe.error(new InvalidClientException("Unable to validate client, assertion signature is not valid."));
            }

            String clientId = jwt.getJWTClaimsSet().getSubject();
            SignedJWT signedJWT = (SignedJWT) jwt;

            return this.clientSyncService.findByClientId(clientId)
                    .switchIfEmpty(Maybe.error(new InvalidClientException("Missing or invalid client")))
                    .flatMap(client -> {
                        try {
                            // Ensure to validate JWT using client_secret_key only if client is authorized to use this auth method
                            if (client.getTokenEndpointAuthMethod() == null ||
                                    ClientAuthenticationMethod.CLIENT_SECRET_JWT.equalsIgnoreCase(client.getTokenEndpointAuthMethod())) {
                                JWSVerifier verifier = new MACVerifier(client.getClientSecret());
                                if (signedJWT.verify(verifier)) {
                                    return Maybe.just(client);
                                }
                            } else {
                                return Maybe.error(new InvalidClientException("Invalid client: missing or unsupported authentication method"));
                            }
                        } catch (JOSEException josee) {
                        }

                        return Maybe.error(new InvalidClientException("Unable to validate client, assertion signature is not valid."));
                    });
        } catch (ClassCastException | ParseException ex) {
            LOGGER.error(ex.getMessage(),ex);
            return Maybe.error(NOT_VALID);
        }
        catch (IllegalArgumentException ex) {
            return Maybe.error(new InvalidClientException(ex.getMessage()));
        }
    }

    private Maybe<JWKSet> getClientJwkSet(Client client) {
        if(client.getJwksUri()!=null && !client.getJwksUri().trim().isEmpty()) {
            return jwkService.getKeys(client.getJwksUri());
        }
        else if(client.getJwks()!=null) {
            return Maybe.just(client.getJwks());
        }
        return Maybe.empty();
    }
}
