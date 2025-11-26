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
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.IDToken;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.utils.MapUtils;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenUtils;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.request.ClaimsRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.gravitee.am.common.oidc.Scope.FULL_PROFILE;
import static io.gravitee.am.common.utils.ConstantKeys.AUTH_FLOW_CONTEXT_ACR_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ID_TOKEN_EXCLUDED_CLAIMS;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.ID_TOKEN;
import static io.gravitee.am.common.oidc.idtoken.Claims.ACR;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class IDTokenServiceImpl implements IDTokenService {

    private static final String DEFAULT_DIGEST_ALGORITHM = "SHA-512";

    @Autowired
    private Domain domain;

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

    @Autowired
    private SubjectManager subjectManager;

    @Deprecated
    @Value("${legacy.openid.openid_scope_full_profile:false}")
    private boolean legacyOpenidScope;

    @Override
    public Single<String> create(OAuth2Request oAuth2Request, Client client, User user, ExecutionContext executionContext) {
        // use or create execution context
        return Single.fromCallable(() -> executionContext != null ? executionContext : createExecution(oAuth2Request, client, user))
                .flatMap(executionContext1 -> {
                    // create JWT ID Token
                    IDToken idToken = createIDTokenJWT(oAuth2Request, client, user, executionContext1);

                    // sign ID Token
                    return certificateManager.findByAlgorithm(client.getIdTokenSignedResponseAlg())
                            .switchIfEmpty(certificateManager.get(client.getCertificate()))
                            .defaultIfEmpty(certificateManager.defaultCertificateProvider())
                            .flatMap(certificateProvider -> {
                                // set hash claims (hybrid flow)
                                if (oAuth2Request.getContext() != null && !oAuth2Request.getContext().isEmpty()) {
                                    oAuth2Request.getContext().forEach((claimName, claimValue) -> {
                                        // Skip ciba_acr_values as it's a List, not a String, and is processed separately for acr claim
                                        if (AUTH_FLOW_CONTEXT_ACR_KEY.equals(claimName)) {
                                            return;
                                        }
                                        // Only process String values for hash claims (hybrid flow)
                                        if (claimValue != null && claimValue instanceof String) {
                                            CertificateMetadata certificateMetadata = certificateProvider.getProvider().certificateMetadata();
                                            String digestAlgorithm;
                                            if (certificateMetadata != null
                                                    && certificateMetadata.getMetadata() != null
                                                    && certificateMetadata.getMetadata().get(CertificateMetadata.DIGEST_ALGORITHM_NAME) != null) {
                                                digestAlgorithm = (String) certificateMetadata.getMetadata().get(CertificateMetadata.DIGEST_ALGORITHM_NAME);
                                            } else {
                                                digestAlgorithm = DEFAULT_DIGEST_ALGORITHM;
                                            }
                                            idToken.addAdditionalClaim(claimName, getHashValue((String) claimValue, digestAlgorithm));
                                        }
                                    });
                                }
                                return jwtService.encode(idToken, certificateProvider);
                            })
                            .flatMap(signedIdToken -> {
                                if (client.getIdTokenEncryptedResponseAlg() != null) {
                                    return jweService.encryptIdToken(signedIdToken, client);
                                }
                                return Single.just(signedIdToken);
                            });
                });
    }

    @Override
    public Single<User> extractUser(String idToken, Client client) {
        return jwtService.decodeAndVerify(idToken, client, ID_TOKEN)
                .flatMap(jwt -> subjectManager.findUserBySub(jwt)
                        .switchIfEmpty(Single.error(() -> new UserNotFoundException(jwt.getSub())))
                        .map(user -> {
                            if (!user.getReferenceId().equals(domain.getId())) {
                                throw new UserNotFoundException(jwt.getSub());
                            }
                            return user;
                        }));
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
        if (oAuth2Request.isClientOnly()) {
            idToken.setSub(oAuth2Request.getClientId());
        } else {
            subjectManager.updateJWT(idToken, user);
        }
        idToken.setAud(oAuth2Request.getClientId());
        idToken.setIat(Instant.now().getEpochSecond());
        idToken.setExp(Instant.ofEpochSecond(idToken.getIat()).plusSeconds(client.getIdTokenValiditySeconds()).getEpochSecond());

        // set auth_time (Time when the End-User authentication occurred.)
        //
        // according to OIDC specification (https://openid.net/specs/openid-connect-core-1_0.html#IDToken),
        // this claim is optional but REQUIRED if the max_age parameter is specified
        // or it the auth_time is part of the claims request.
        // we decided to always provide this claim during the Financial-grand API conformance implementation
        // since this claim was return by default in some cases even if conditions that require it were missing
        if (!oAuth2Request.isClientOnly() && user != null && user.getLoggedAt() != null) {
            idToken.setAuthTime(user.getLoggedAt().getTime() / 1000L);
        }

        // set nonce
        String nonce = oAuth2Request.parameters() != null ? oAuth2Request.parameters().getFirst(Parameters.NONCE) : null;
        if (nonce != null && !nonce.isEmpty()) {
            idToken.setNonce(nonce);
        }

        // set acr claim from CIBA request acrValues (if present)
        // For CIBA flow, acrValues are stored in the token request context during token grant
        if (oAuth2Request.getContext() != null) {
            MapUtils.extractStringList(oAuth2Request.getContext(), AUTH_FLOW_CONTEXT_ACR_KEY)
                    .filter(acrValues -> !acrValues.isEmpty())
                    .ifPresent(acrValues -> {
                        // For FAPI-CIBA, use the highest-level requested value (typically the last one in the list)
                        // or the one that was met. For simplicity, we'll use the last value in the list.
                        idToken.addAdditionalClaim(ACR, acrValues.getLast());
                    });
        }

        // processing claims list
        if (!oAuth2Request.isClientOnly() && user != null && user.getAdditionalInformation() != null) {
            boolean requestForSpecificClaims = false;
            Map<String, Object> userClaims = new HashMap<>();
            Map<String, Object> fullProfileClaims = new HashMap<>(user.getAdditionalInformation());
            // to be sure that this sub value coming from the IDP will not override the one provided by AM
            // we explicitly remove it from the additional info.
            // see https://github.com/gravitee-io/issues/issues/7118
            fullProfileClaims.remove(StandardClaims.SUB);

            // 1. process the request using scope values
            if (oAuth2Request.getScopes() != null) {
                requestForSpecificClaims = processScopesRequest(oAuth2Request.getScopes(), userClaims, fullProfileClaims, idToken);
            }

            // 2. process the request using the claims values (If present, the listed Claims are being requested to be added to the default Claims in the ID Token)
            if (oAuth2Request.parameters() != null && oAuth2Request.parameters().getFirst(Parameters.CLAIMS) != null) {
                requestForSpecificClaims = processClaimsRequest(oAuth2Request.parameters().getFirst(Parameters.CLAIMS), fullProfileClaims, idToken);
            }

            // 3. If no claims requested, grab all user claims
            if (!requestForSpecificClaims) {
                userClaims.forEach((k, v) -> {
                    if (!ID_TOKEN_EXCLUDED_CLAIMS.contains(k)) {
                        idToken.addAdditionalClaim(k, v);
                    }
                });
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
     * @param userClaims user claims list
     * @param fullProfileClaims full claims list
     * @param requestedClaims requested claims
     * @return true if OpenID Connect scopes have been found
     */
    private boolean processScopesRequest(Set<String> scopes, Map<String, Object> userClaims, final Map<String, Object> fullProfileClaims, Map<String, Object> requestedClaims) {
        // if full_profile requested, continue
        // if legacy mode is enabled, also return all if only openid scope is provided
        if (scopes.contains(FULL_PROFILE.getKey()) ||
                (legacyOpenidScope && scopes.size() == 1 && scopes.contains(Scope.OPENID.getKey()))) {
            userClaims.putAll(fullProfileClaims);
            return false;
        }

        // get requested scopes claims
        final List<String> scopesClaims = scopes.stream()
                .map(String::toUpperCase)
                .filter(scope -> Scope.exists(scope) && !Scope.valueOf(scope).getClaims().isEmpty())
                .map(Scope::valueOf)
                .map(Scope::getClaims)
                .flatMap(List::stream)
                .toList();

        // no OpenID Connect scopes requested continue
        if (scopesClaims.isEmpty()) {
            return false;
        }

        // return specific available sets of information made by scope value request
        scopesClaims.forEach(scopeClaim -> {
            if (fullProfileClaims.containsKey(scopeClaim)) {
                requestedClaims.putIfAbsent(scopeClaim, fullProfileClaims.get(scopeClaim));
            }
        });

        return true;
    }

    /**
     * Handle claims request previously made during the authorization request
     * @param claimsValue claims request parameter
     * @param fullProfileClaims user full claims list
     * @param idToken requested claims
     * @return true if id_token claims have been found
     */
    private boolean processClaimsRequest(String claimsValue, final Map<String, Object> fullProfileClaims, IDToken idToken) {
        try {
            ClaimsRequest claimsRequest = objectMapper.readValue(claimsValue, ClaimsRequest.class);
            if (claimsRequest != null && claimsRequest.getIdTokenClaims() != null) {
                claimsRequest.getIdTokenClaims().forEach((key, claimRequest) -> {
                    if (fullProfileClaims.containsKey(key)) {
                        idToken.addAdditionalClaim(key, fullProfileClaims.get(key));
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
                                if (Claims.AUD.equals(claimName) && (extValue instanceof String[] || extValue instanceof List)) {
                                    var audiences = new LinkedHashSet<>();
                                    audiences.add(jwt.getAud()); // make sure the client_id is the first entry of the aud array
                                    audiences.addAll(extValue instanceof List ? (List)extValue : List.of((String[]) extValue)); // Set will remove duplicate client_id if any
                                    var jsonArray = new JSONArray();
                                    jsonArray.addAll(audiences);
                                    jwt.put(claimName, jsonArray);
                                } else {
                                    jwt.put(claimName, extValue);
                                }
                            }
                        } catch (Exception ex) {
                            log.debug("An error occurs while parsing expression language : {}", tokenClaim.getClaimValue(), ex);
                        }
                    });
        }
    }


    private ExecutionContext createExecution(OAuth2Request request, Client client, User user) {
        ExecutionContext simpleExecutionContext = new SimpleExecutionContext(request, null);
        ExecutionContext executionContext = executionContextFactory.create(simpleExecutionContext);

        // put auth flow policy context attributes in context
        Object authFlowAttributes = request.getContext().get(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY);
        if (authFlowAttributes != null) {
            executionContext.setAttribute(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY, authFlowAttributes);
            request.getContext().remove(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY);
        }
        // put oauth2 request execution context attributes in context
        executionContext.getAttributes().putAll(request.getExecutionContext());
        executionContext.setAttribute(ConstantKeys.CLIENT_CONTEXT_KEY, new ClientProperties(client));
        if (user != null) {
            executionContext.setAttribute(ConstantKeys.USER_CONTEXT_KEY, new UserProperties(user, true));
        }

        return executionContext;
    }
}
