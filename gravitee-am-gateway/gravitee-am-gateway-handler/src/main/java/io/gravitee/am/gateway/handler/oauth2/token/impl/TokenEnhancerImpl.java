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
import io.gravitee.am.gateway.handler.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.oidc.utils.OIDCClaims;
import io.gravitee.am.gateway.handler.role.RoleService;
import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.crypto.MacProvider;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.security.Key;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenEnhancerImpl implements TokenEnhancer, InitializingBean {

    private static final int defaultIDTokenExpireIn = 14400;
    private static final String OPEN_ID = "openid";
    private static final String ID_TOKEN = "id_token";
    private ObjectMapper objectMapper = new ObjectMapper();
    private JwtBuilder jwtBuilder;

    @Value("${oidc.iss:http://gravitee.am}")
    private String iss;

    @Value("${oidc.signing.key.secret:s3cR3t4grAv1t33}")
    private String signingKeySecret;

    @Autowired
    private ClientService clientService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private CertificateManager certificateManager;

    @Override
    public void afterPropertiesSet() {
        // create default signing HMAC key
        Key key = MacProvider.generateKey(SignatureAlgorithm.HS512, new SecureRandom(signingKeySecret.getBytes()));
        jwtBuilder = Jwts.builder().signWith(SignatureAlgorithm.HS512, key);
    }

    @Override
    public Single<AccessToken> enhance(AccessToken accessToken, OAuth2Request oAuth2Request) {
        return clientService.findByClientId(oAuth2Request.getClientId())
                .switchIfEmpty(Maybe.error(new ClientNotFoundException(oAuth2Request.getClientId())))
                .flatMapSingle(client -> {
                    if (!oAuth2Request.isClientOnly()) {
                        return userService.findById(oAuth2Request.getSubject())
                                .switchIfEmpty(Maybe.error(new UserNotFoundException(oAuth2Request.getSubject())))
                                .toSingle()
                                .map(user -> new TokenEnhancerData(client, user));
                    } else {
                        return Single.just(new TokenEnhancerData(client, null));
                    }
                })
                .flatMap(tokenEnhancerData -> Single.just(tokenEnhancerData.getUser() == null)
                        .flatMap(isClientOnly -> {
                            if (!isClientOnly) {
                                // enhance token scopes with user permissions
                                return enhanceScopes(accessToken, tokenEnhancerData.getUser(), oAuth2Request);
                            } else {
                                return Single.just(accessToken);
                            }
                        })
                        .flatMap(accessToken1 -> {
                            // enhance token with ID token
                            if (oAuth2Request.getScopes() != null && oAuth2Request.getScopes().contains(OPEN_ID)) {
                                return enhanceIDToken(accessToken1, tokenEnhancerData.getClient(), tokenEnhancerData.getUser(), oAuth2Request);
                            } else {
                                return Single.just(accessToken1);
                            }
                        }));

    }

    private Single<AccessToken> enhanceScopes(AccessToken accessToken, User user, OAuth2Request oAuth2Request) {
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            return roleService.findByIdIn(user.getRoles())
                    .zipWith((SingleSource<Set<String>>) observer -> {
                        // get requested scopes
                        Set<String> requestedScopes = new HashSet<>();
                        String scope = oAuth2Request.getRequestParameters().getFirst(OAuth2Constants.SCOPE);
                        if (scope != null) {
                            requestedScopes = new HashSet<>(Arrays.asList(scope.split(" ")));
                        }
                        observer.onSuccess(requestedScopes);
                    }, (roles, requestedScopes) -> {
                        Set<String> enhanceScopes = new HashSet<>(accessToken.getScopes());
                        enhanceScopes.addAll(roles.stream()
                                .map(r -> r.getPermissions())
                                .flatMap(List::stream)
                                .filter(permission -> {
                                    if (requestedScopes != null && !requestedScopes.isEmpty()) {
                                        return requestedScopes.contains(permission);
                                    }
                                    // if no query param scope, accept all enhance scopes
                                    return true;
                                })
                                .collect(Collectors.toList()));
                        accessToken.setScopes(enhanceScopes);
                        return accessToken;
                    });
        } else {
            return Single.just(accessToken);
        }
    }

    private Single<AccessToken> enhanceIDToken(AccessToken accessToken, Client client, User user, OAuth2Request oAuth2Request) {
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

        // override claims for an end-user
        if (!oAuth2Request.isClientOnly() && client.getIdTokenCustomClaims() != null) {
            if (user.getAdditionalInformation() != null && !user.getAdditionalInformation().isEmpty()) {
                final Map<String, Object> userAdditionalInformation = user.getAdditionalInformation();
                client.getIdTokenCustomClaims().forEach((key, value) -> {
                    if (userAdditionalInformation.get(value) != null) {
                        IDToken.put(key, userAdditionalInformation.get(value));
                    }
                });
            }
        }

        // sign the ID Token and add id_token field to the access_token
        return certificateManager.get(client.getCertificate())
                .map(certificateProvider -> certificateProvider.sign(objectMapper.writeValueAsString(IDToken)))
                .defaultIfEmpty(jwtBuilder.setClaims(IDToken).compact())
                .flatMapSingle(payload -> {
                    Map<String, Object> additionalInformation = new HashMap<>(accessToken.getAdditionalInformation());
                    additionalInformation.put(ID_TOKEN, payload);
                    accessToken.setAdditionalInformation(additionalInformation);
                    return Single.just(accessToken);
                });
    }

    public void setJwtBuilder(JwtBuilder jwtBuilder) {
        this.jwtBuilder = jwtBuilder;
    }

    private class TokenEnhancerData {
        private Client client;
        private User user;

        public TokenEnhancerData(Client client, User user) {
            this.client = client;
            this.user = user;
        }

        public Client getClient() {
            return client;
        }

        public void setClient(Client client) {
            this.client = client;
        }

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }
    }

}
