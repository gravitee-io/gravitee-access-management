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
package io.gravitee.am.gateway.handler.oauth2.token.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.gateway.handler.oauth2.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oauth2.utils.OIDCParameters;
import io.gravitee.am.gateway.handler.oidc.request.ClaimsRequest;
import io.gravitee.am.gateway.handler.oidc.utils.OIDCClaims;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.common.util.MultiValueMap;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.crypto.MacProvider;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.security.Key;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenEnhancerImpl implements TokenEnhancer, InitializingBean {

    private static final int defaultIDTokenExpireIn = 14400;
    private static final String OPEN_ID = "openid";
    private static final String ID_TOKEN = "id_token";
    private ObjectMapper objectMapper = new ObjectMapper();
    private CertificateProvider defaultCertificateProvider;

    @Value("${oidc.iss:http://gravitee.am}")
    private String iss;

    @Value("${oidc.signing.key.secret:s3cR3t4grAv1t33}")
    private String signingKeySecret;

    @Value("${oidc.signing.key.kid:default-gravitee-AM-key}")
    private String signingKeyId;

    @Autowired
    private CertificateManager certificateManager;

    @Override
    public void afterPropertiesSet() {
        // create default signing HMAC key
        Key key = MacProvider.generateKey(SignatureAlgorithm.HS512, new SecureRandom(signingKeySecret.getBytes()));
        JwtBuilder jwtBuilder = Jwts.builder().signWith(SignatureAlgorithm.HS512, key);

        // create default certificate provider
        setDefaultCertificateProvider(jwtBuilder);
    }

    @Override
    public Single<AccessToken> enhance(AccessToken accessToken, OAuth2Request oAuth2Request, Client client, User endUser) {
        // enhance token with ID token
        if (oAuth2Request.getScopes() != null && oAuth2Request.getScopes().contains(OPEN_ID)) {
            return enhanceIDToken(accessToken, oAuth2Request, client, endUser);
        }

        return Single.just(accessToken);
    }

    private Single<AccessToken> enhanceIDToken(AccessToken accessToken, OAuth2Request oAuth2Request, Client client, User user) {
        // create ID token
        Map<String, Object> IDToken = new HashMap<>();
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        IDToken.put(OIDCClaims.iss, iss);
        IDToken.put(OIDCClaims.sub, oAuth2Request.isClientOnly() ? oAuth2Request.getClientId() : user.getUsername());
        IDToken.put(OIDCClaims.aud, oAuth2Request.getClientId());
        IDToken.put(OIDCClaims.iat, calendar.getTimeInMillis() / 1000l);

        // set expiration time
        calendar.add(Calendar.SECOND, client.getIdTokenValiditySeconds() > 0 ? client.getIdTokenValiditySeconds() : defaultIDTokenExpireIn);
        IDToken.put(OIDCClaims.exp, calendar.getTimeInMillis() / 1000l);

        // set nonce
        String nonce = oAuth2Request.getRequestParameters().getFirst(OIDCClaims.nonce);
        if (nonce != null && !nonce.isEmpty()) {
            IDToken.put(OIDCClaims.nonce, nonce);
        }

        // set auth_time (Time when the End-User authentication occurred.)
        if (!oAuth2Request.isClientOnly() && user != null && user.getLoggedAt() != null) {
            String maxAge = oAuth2Request.getRequestParameters().getFirst(OIDCParameters.MAX_AGE);
            if (maxAge != null) {
                IDToken.put(OIDCClaims.auth_time, user.getLoggedAt().getTime() / 1000l);
            }
        }

        // override claims for an end-user
        if (!oAuth2Request.isClientOnly() && user.getAdditionalInformation() != null && !user.getAdditionalInformation().isEmpty()) {
            final Map<String, Object> userAdditionalInformation = user.getAdditionalInformation();
            boolean requestForSpecificClaims = false;
            // processing claims list
            // 1. process the request using the claims values (If present, the listed Claims are being requested to be added to the default Claims in the ID Token)
            // 2. If not present, check if the client has enabled the ID token mapping claims.
            // 3. Else send all user claims
            MultiValueMap<String, String> requestedParameters = oAuth2Request.getRequestParameters();
            if (requestedParameters != null && requestedParameters.getFirst(OIDCParameters.CLAIMS) != null) {
                requestForSpecificClaims = processClaimsRequest(requestedParameters.getFirst(OIDCParameters.CLAIMS), userAdditionalInformation, IDToken);
            } else if (client.getIdTokenCustomClaims() != null) {
                client.getIdTokenCustomClaims().forEach((key, value) -> {
                    if (userAdditionalInformation.get(value) != null) {
                        IDToken.put(key, userAdditionalInformation.get(value));
                    }
                });
                requestForSpecificClaims = true;
            }
            if (!requestForSpecificClaims) {
                IDToken.putAll(userAdditionalInformation);
            }
        }

        // sign the ID Token and add id_token field to the access_token
        return certificateManager.get(client.getCertificate())
                .defaultIfEmpty(defaultCertificateProvider)
                .flatMapSingle(certificateProvider ->  certificateProvider.sign(objectMapper.writeValueAsString(IDToken)))
                .flatMap(payload -> {
                    Map<String, Object> additionalInformation = new HashMap<>(accessToken.getAdditionalInformation());
                    additionalInformation.put(ID_TOKEN, payload);
                    accessToken.setAdditionalInformation(additionalInformation);
                    return Single.just(accessToken);
                });
    }

    public void setJwtBuilder(JwtBuilder jwtBuilder) {
        setDefaultCertificateProvider(jwtBuilder);
    }

    private void setDefaultCertificateProvider(JwtBuilder jwtBuilder) {
        defaultCertificateProvider = new CertificateProvider() {
            @Override
            public Single<String> sign(String payload) {
                return Single.just(jwtBuilder.setPayload(payload).compact());
            }

            @Override
            public Single<String> publicKey() {
                return null;
            }

            @Override
            public Flowable<JWK> keys() {
                return null;
            }
        };
    }

    /**
     * Handle claims request previously made during the authorization request
     * @param claimsValue claims request parameter
     * @param userClaims user full claims list
     * @param requestedClaims requested claims
     * @return true if id_token claims have been found
     */
    private boolean processClaimsRequest(String claimsValue, final Map<String, Object> userClaims, Map<String, Object> requestedClaims) {
        try {
            ClaimsRequest claimsRequest = objectMapper.readValue(claimsValue, ClaimsRequest.class);
            if (claimsRequest != null && claimsRequest.getIdTokenClaims() != null) {
                claimsRequest.getIdTokenClaims().forEach((key, value) -> {
                    if (userClaims.containsKey(key)) {
                        requestedClaims.putIfAbsent(key, userClaims.get(key));
                    }
                });
                return true;
            }
        } catch (Exception e) {
            // Any members used that are not understood MUST be ignored.
        }
        return false;
    }
}
