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
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.common.oidc.idtoken.IDToken;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenUtils;
import io.gravitee.am.gateway.handler.oidc.service.request.ClaimsRequest;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IDTokenServiceImpl implements IDTokenService {

    private static final int defaultIDTokenExpireIn = 14400;
    private static final String defaultDigestAlgorithm = "SHA-512";

    @Autowired
    private CertificateManager certificateManager;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Override
    public Single<String> create(OAuth2Request oAuth2Request, Client client, User user) {
        IDToken idToken = new IDToken();

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        idToken.setIss(openIDDiscoveryService.getIssuer(oAuth2Request.getOrigin()));
        idToken.setSub(oAuth2Request.isClientOnly() ? oAuth2Request.getClientId() : user.getId());
        idToken.setAud(oAuth2Request.getClientId());
        idToken.setIat(calendar.getTimeInMillis() / 1000l);

        // set expiration time
        calendar.add(Calendar.SECOND, client.getIdTokenValiditySeconds() > 0 ? client.getIdTokenValiditySeconds() : defaultIDTokenExpireIn);
        idToken.setExp(calendar.getTimeInMillis() / 1000l);

        // set nonce
        String nonce = oAuth2Request.getRequestParameters().getFirst(Parameters.NONCE);
        if (nonce != null && !nonce.isEmpty()) {
            idToken.setNonce(nonce);
        }

        // set auth_time (Time when the End-User authentication occurred.)
        if (!oAuth2Request.isClientOnly() && user != null && user.getLoggedAt() != null) {
            String maxAge = oAuth2Request.getRequestParameters().getFirst(Parameters.MAX_AGE);
            if (maxAge != null) {
                idToken.setAuthTime(user.getLoggedAt().getTime() / 1000l);
            }
        }

        // override claims for an end-user
        if (!oAuth2Request.isClientOnly() && user.getAdditionalInformation() != null && !user.getAdditionalInformation().isEmpty()) {
            final Map<String, Object> userAdditionalInformation = user.getAdditionalInformation();
            boolean requestForSpecificClaims = false;
            // processing claims list
            // 1. process the request using scope values
            if (oAuth2Request.getScopes() != null) {
                requestForSpecificClaims = processScopesRequest(oAuth2Request.getScopes(), userAdditionalInformation, idToken);
            }
            MultiValueMap<String, String> requestedParameters = oAuth2Request.getRequestParameters();
            // 2. process the request using the claims values (If present, the listed Claims are being requested to be added to the default Claims in the ID Token)
            if (requestedParameters != null && requestedParameters.getFirst(Parameters.CLAIMS) != null) {
                requestForSpecificClaims = processClaimsRequest(requestedParameters.getFirst(Parameters.CLAIMS), userAdditionalInformation, idToken);
            // 3. If not present, check if the client has enabled the ID token mapping claims.
            } else if (client.getIdTokenCustomClaims() != null && !client.getIdTokenCustomClaims().isEmpty()) {
                client.getIdTokenCustomClaims().forEach((key, value) -> {
                    if (userAdditionalInformation.get(value) != null) {
                        idToken.addAdditionalClaim(key, userAdditionalInformation.get(value));
                    }
                });
                requestForSpecificClaims = true;
            }
            // 4. Else send all user claims
            if (!requestForSpecificClaims) {
                userAdditionalInformation.forEach((k, v) -> idToken.addAdditionalClaim(k, v));
            }
        }

        // sign the ID Token and add id_token field to the access_token
        return certificateManager.findByAlgorithm(client.getIdTokenSignedResponseAlg())
                .switchIfEmpty(certificateManager.get(client.getCertificate()))
                .defaultIfEmpty(certificateManager.defaultCertificateProvider())
                .flatMapSingle(certificateProvider -> {
                    // set hash claims (hybrid flow)
                    if (oAuth2Request.getContext() != null && !oAuth2Request.getContext().isEmpty()) {
                        oAuth2Request.getContext().forEach((claimName, claimValue) -> {
                            CertificateMetadata certificateMetadata = certificateProvider.getProvider().certificateMetadata();
                            String digestAlgorithm = defaultDigestAlgorithm;
                            if (certificateMetadata != null
                                    && certificateMetadata.getMetadata() != null
                                    && certificateMetadata.getMetadata().get(CertificateMetadata.DIGEST_ALGORITHM_NAME) != null) {
                                digestAlgorithm = (String) certificateMetadata.getMetadata().get(CertificateMetadata.DIGEST_ALGORITHM_NAME);
                            }
                            idToken.addAdditionalClaim(claimName, getHashValue((String) claimValue, digestAlgorithm));
                        });
                    }
                    return jwtService.encode(idToken, certificateProvider);
                });
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
                claimsRequest.getIdTokenClaims().forEach((key, value) -> {
                    if (userClaims.containsKey(key)) {
                        idToken.addAdditionalClaim(key, userClaims.get(key));
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
}
