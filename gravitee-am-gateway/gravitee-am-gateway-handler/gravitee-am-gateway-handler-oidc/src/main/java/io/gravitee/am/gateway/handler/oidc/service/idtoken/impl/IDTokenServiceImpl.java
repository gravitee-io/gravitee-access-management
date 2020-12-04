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
package io.gravitee.am.gateway.handler.oidc.service.idtoken.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.common.oidc.idtoken.IDToken;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.context.provider.ClientProperties;
import io.gravitee.am.gateway.handler.context.provider.UserProperties;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenUtils;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.request.ClaimsRequest;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IDTokenServiceImpl implements IDTokenService {

    private static final Logger logger = LoggerFactory.getLogger(IDTokenServiceImpl.class);
    private static final String defaultDigestAlgorithm = "SHA-512";

    @Autowired
    private CertificateManager certificateManager;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private JWEService jweService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Autowired
    private ExecutionContextFactory executionContextFactory;

    @Override
    public Single<String> create(OAuth2Request oAuth2Request, Client client, User user, ExecutionContext executionContext) {
        // use or create execution context
        return Single.fromCallable(() -> executionContext != null ? executionContext : createExecution(oAuth2Request, client, user))
                .flatMap(executionContext1 -> {
                    // create JWT ID Token
                    IDToken idToken = createIDTokenJWT(oAuth2Request, client, user, executionContext);

                    // sign ID Token
                    return certificateManager.findByAlgorithm(client.getIdTokenSignedResponseAlg())
                            .switchIfEmpty(certificateManager.get(client.getCertificate()))
                            .defaultIfEmpty(certificateManager.defaultCertificateProvider())
                            .flatMapSingle(certificateProvider -> {
                                // set hash claims (hybrid flow)
                                if (oAuth2Request.getContext() != null && !oAuth2Request.getContext().isEmpty()) {
                                    oAuth2Request.getContext().forEach((claimName, claimValue) -> {
                                        if (claimValue != null) {
                                            CertificateMetadata certificateMetadata = certificateProvider.getProvider().certificateMetadata();
                                            String digestAlgorithm = defaultDigestAlgorithm;
                                            if (certificateMetadata != null
                                                    && certificateMetadata.getMetadata() != null
                                                    && certificateMetadata.getMetadata().get(CertificateMetadata.DIGEST_ALGORITHM_NAME) != null) {
                                                digestAlgorithm = (String) certificateMetadata.getMetadata().get(CertificateMetadata.DIGEST_ALGORITHM_NAME);
                                            }
                                            idToken.addAdditionalClaim(claimName, getHashValue((String) claimValue, digestAlgorithm));
                                        }
                                    });
                                }
                                return jwtService.encode(idToken, certificateProvider);
                            })
                            .flatMap(signedIdToken -> {
                                if(client.getIdTokenEncryptedResponseAlg()!=null) {
                                    return jweService.encryptIdToken(signedIdToken, client);
                                }
                                return Single.just(signedIdToken);
                            });
                });
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Create ID Token. The ID Token is represented as a JSON Web Token (JWT) and contains some required claims
     *
     * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDToken">2.ID Token</a>
     *
     * @param oAuth2Request OAuth 2.0 request
     * @param client OAuth 2.0 client
     * @param user OAuth 2.0 end user
     * @param executionContext the execuiton context
     * @return an ID Token
     */
    private IDToken createIDTokenJWT(OAuth2Request oAuth2Request, Client client, User user, ExecutionContext executionContext) {
        IDToken idToken = new IDToken();
        // set required claims
        idToken.setIss(openIDDiscoveryService.getIssuer(oAuth2Request.getOrigin()));
        idToken.setSub(oAuth2Request.isClientOnly() ? oAuth2Request.getClientId() : user.getId());
        idToken.setAud(oAuth2Request.getClientId());
        idToken.setIat(Instant.now().getEpochSecond());
        idToken.setExp(Instant.ofEpochSecond(idToken.getIat()).plusSeconds(client.getIdTokenValiditySeconds()).getEpochSecond());
        // set auth_time (Time when the End-User authentication occurred.)
        if (!oAuth2Request.isClientOnly() && user != null && user.getLoggedAt() != null) {
            String maxAge = oAuth2Request.parameters().getFirst(Parameters.MAX_AGE);
            if (maxAge != null) {
                idToken.setAuthTime(user.getLoggedAt().getTime() / 1000l);
            }
        }
        // set nonce
        String nonce = oAuth2Request.parameters() != null ? oAuth2Request.parameters().getFirst(Parameters.NONCE) : null;
        if (nonce != null && !nonce.isEmpty()) {
            idToken.setNonce(nonce);
        }

        // processing claims list
        if (!oAuth2Request.isClientOnly() && user != null && user.getAdditionalInformation() != null) {
            boolean requestForSpecificClaims = false;
            Map<String, Object> userClaims = user.getAdditionalInformation();
            // 1. process the request using scope values
            if (oAuth2Request.getScopes() != null) {
                requestForSpecificClaims = processScopesRequest(oAuth2Request.getScopes(), userClaims, idToken);
            }

            // 2. process the request using the claims values (If present, the listed Claims are being requested to be added to the default Claims in the ID Token)
            if (oAuth2Request.parameters() != null && oAuth2Request.parameters().getFirst(Parameters.CLAIMS) != null) {
                requestForSpecificClaims = processClaimsRequest(oAuth2Request.parameters().getFirst(Parameters.CLAIMS), userClaims, idToken);
            }

            // 3. If no claims requested, grab all user claims
            if (!requestForSpecificClaims) {
                userClaims.forEach((k, v) -> idToken.addAdditionalClaim(k, v));
            }
        }

        // 4. Enhance ID token with custom claims
        enhanceIDToken(idToken, client.getTokenCustomClaims(), executionContext);

        return idToken;
    }

    /**
     * For OpenID Connect, scopes can be used to request that specific sets of information be made available as Claim Values.
     *
     * @param scopes scopes request parameter
     * @param userClaims user full claims list
     * @param requestedClaims requested claims
     * @return true if OpenID Connect scopes have been found
     */
    private boolean processScopesRequest(Set<String> scopes, final Map<String, Object> userClaims, Map<String, Object> requestedClaims) {
        // get requested scopes claims
        final List<String> scopesClaims = scopes.stream()
                .map(scope -> scope.toUpperCase())
                .filter(scope -> Scope.exists(scope) && !Scope.valueOf(scope).getClaims().isEmpty())
                .map(scope -> Scope.valueOf(scope))
                .map(scope -> scope.getClaims())
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // no OpenID Connect scopes requested continue
        if (scopesClaims.isEmpty()) {
            return false;
        }

        // return specific available sets of information made by scope value request
        scopesClaims.forEach(scopeClaim -> {
            if (userClaims.containsKey(scopeClaim)) {
                requestedClaims.putIfAbsent(scopeClaim, userClaims.get(scopeClaim));
            }
        });

        return true;
    }

    /**
     * Handle claims request previously made during the authorization request
     * @param claimsValue claims request parameter
     * @param userClaims user full claims list
     * @param idToken requested claims
     * @return true if id_token claims have been found
     */
    private boolean processClaimsRequest(String claimsValue, final Map<String, Object> userClaims, IDToken idToken) {
        try {
            ClaimsRequest claimsRequest = objectMapper.readValue(claimsValue, ClaimsRequest.class);
            if (claimsRequest != null && claimsRequest.getIdTokenClaims() != null) {
                claimsRequest.getIdTokenClaims().forEach((key, claimRequest) -> {
                    if (userClaims.containsKey(key)) {
                        idToken.addAdditionalClaim(key, userClaims.get(key));
                    } else {
                        if (claimRequest.getValues() != null) {
                            idToken.addAdditionalClaim(key, claimRequest.getValues());
                        } else if (claimRequest.getValue() != null) {
                            idToken.addAdditionalClaim(key, claimRequest.getValue());
                        }
                    }
                });
                return true;
            }
        } catch (Exception e) {
            // Any members used that are not understood MUST be ignored.
        }
        return false;
    }

    private String getHashValue(String payload, String digestAlgorithm) {
        return IDTokenUtils.generateHashValue(payload, digestAlgorithm);
    }

    private void enhanceIDToken(JWT jwt, List<TokenClaim> customClaims, ExecutionContext executionContext) {
        if (customClaims != null && !customClaims.isEmpty()) {
            customClaims
                    .stream()
                    .filter(tokenClaim -> TokenTypeHint.ID_TOKEN.equals(tokenClaim.getTokenType()))
                    .forEach(tokenClaim -> {
                        try {
                            String claimName = tokenClaim.getClaimName();
                            String claimExpression = tokenClaim.getClaimValue();
                            Object extValue = (claimExpression != null) ? executionContext.getTemplateEngine().getValue(claimExpression, Object.class) : null;
                            if (extValue != null) {
                                jwt.put(claimName, extValue);
                            }
                        } catch (Exception ex) {
                            logger.debug("An error occurs while parsing expression language : {}", tokenClaim.getClaimValue(), ex);
                        }
                    });
        }
    }


    private ExecutionContext createExecution(OAuth2Request request, Client client, User user) {
        ExecutionContext simpleExecutionContext = new SimpleExecutionContext(request, null);
        ExecutionContext executionContext = executionContextFactory.create(simpleExecutionContext);
        executionContext.setAttribute("client", new ClientProperties(client));
        if (user != null) {
            executionContext.setAttribute("user", new UserProperties(user));
        }
        return executionContext;
    }
}
