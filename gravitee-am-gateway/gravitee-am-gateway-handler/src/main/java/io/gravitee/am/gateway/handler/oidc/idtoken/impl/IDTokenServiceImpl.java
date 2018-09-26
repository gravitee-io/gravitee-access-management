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
package io.gravitee.am.gateway.handler.oidc.idtoken.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.gateway.handler.oauth2.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.utils.OIDCParameters;
import io.gravitee.am.gateway.handler.oidc.idtoken.IDToken;
import io.gravitee.am.gateway.handler.oidc.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.oidc.idtoken.IDTokenUtils;
import io.gravitee.am.gateway.handler.oidc.request.ClaimsRequest;
import io.gravitee.am.gateway.handler.oidc.utils.OIDCClaims;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.common.util.MultiValueMap;
import io.jsonwebtoken.JwsHeader;
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
import java.util.Collections;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IDTokenServiceImpl implements IDTokenService, InitializingBean {

    private static final int defaultIDTokenExpireIn = 14400;
    private static final String defaultDigestAlgorithm = "SHA-512";
    private ObjectMapper objectMapper = new ObjectMapper();

    @Value("${oidc.iss:http://gravitee.am}")
    private String iss;

    @Value("${oidc.signing.key.secret:s3cR3t4grAv1t33}")
    private String signingKeySecret;

    @Value("${oidc.signing.key.kid:default-gravitee-AM-key}")
    private String signingKeyId;

    private CertificateProvider defaultCertificateProvider;

    @Autowired
    private CertificateManager certificateManager;

    @Override
    public Single<String> create(OAuth2Request oAuth2Request, Client client, User user) {
        IDToken idToken = new IDToken();

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        idToken.setIss(iss);
        idToken.setSub(oAuth2Request.isClientOnly() ? oAuth2Request.getClientId() : user.getUsername());
        idToken.setAud(oAuth2Request.getClientId());
        idToken.setIat(calendar.getTimeInMillis() / 1000l);

        // set expiration time
        calendar.add(Calendar.SECOND, client.getIdTokenValiditySeconds() > 0 ? client.getIdTokenValiditySeconds() : defaultIDTokenExpireIn);
        idToken.setExp(calendar.getTimeInMillis() / 1000l);

        // set nonce
        String nonce = oAuth2Request.getRequestParameters().getFirst(OIDCClaims.nonce);
        if (nonce != null && !nonce.isEmpty()) {
            idToken.setNonce(nonce);
        }

        // set auth_time (Time when the End-User authentication occurred.)
        if (!oAuth2Request.isClientOnly() && user != null && user.getLoggedAt() != null) {
            String maxAge = oAuth2Request.getRequestParameters().getFirst(OIDCParameters.MAX_AGE);
            if (maxAge != null) {
                idToken.setAuthTime(user.getLoggedAt().getTime() / 1000l);
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
                requestForSpecificClaims = processClaimsRequest(requestedParameters.getFirst(OIDCParameters.CLAIMS), userAdditionalInformation, idToken);
            } else if (client.getIdTokenCustomClaims() != null) {
                client.getIdTokenCustomClaims().forEach((key, value) -> {
                    if (userAdditionalInformation.get(value) != null) {
                        idToken.addAdditionalClaim(key, userAdditionalInformation.get(value));
                    }
                });
                requestForSpecificClaims = true;
            }
            if (!requestForSpecificClaims) {
                idToken.setAdditionalClaims(userAdditionalInformation);
            }
        }

        // sign the ID Token and add id_token field to the access_token
        return certificateManager.get(client.getCertificate())
                .defaultIfEmpty(defaultCertificateProvider)
                .flatMapSingle(certificateProvider -> {
                    // set hash claims (hybrid flow)
                    if (oAuth2Request.getContext() != null && !oAuth2Request.getContext().isEmpty()) {
                        oAuth2Request.getContext().forEach((claimName, claimValue) -> {
                            CertificateMetadata certificateMetadata = certificateProvider.certificateMetadata();
                            String digestAlgorithm = defaultDigestAlgorithm;
                            if (certificateMetadata != null
                                    && certificateMetadata.getMetadata() != null
                                    && certificateMetadata.getMetadata().get(CertificateMetadata.DIGEST_ALGORITHM_NAME) != null) {
                                digestAlgorithm = (String) certificateMetadata.getMetadata().get(CertificateMetadata.DIGEST_ALGORITHM_NAME);
                            }
                            idToken.addAdditionalClaim(claimName, getHashValue((String) claimValue, digestAlgorithm));
                        });
                    }
                    return certificateProvider.sign(objectMapper.writeValueAsString(idToken));
                });
    }

    public void setJwtBuilder(JwtBuilder jwtBuilder) {
        setDefaultCertificateProvider(jwtBuilder);
    }

    @Override
    public void afterPropertiesSet() {
        // create default signing HMAC key
        Key key = MacProvider.generateKey(SignatureAlgorithm.HS512, new SecureRandom(signingKeySecret.getBytes()));
        JwtBuilder jwtBuilder = Jwts.builder().signWith(SignatureAlgorithm.HS512, key);

        // create default certificate provider
        setDefaultCertificateProvider(jwtBuilder);
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

    private void setDefaultCertificateProvider(JwtBuilder jwtBuilder) {
        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, defaultDigestAlgorithm));

        defaultCertificateProvider = new CertificateProvider() {
            @Override
            public Single<String> sign(String payload) {
                return Single.just(jwtBuilder.setHeaderParam(JwsHeader.KEY_ID,  signingKeyId).setPayload(payload).compact());
            }

            @Override
            public Single<String> publicKey() {
                return null;
            }

            @Override
            public Flowable<JWK> keys() {
                return null;
            }

            @Override
            public CertificateMetadata certificateMetadata() {
                return certificateMetadata;
            }
        };
    }
}
