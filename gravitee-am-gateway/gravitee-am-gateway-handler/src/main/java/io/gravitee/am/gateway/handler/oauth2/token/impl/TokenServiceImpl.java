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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.exception.JwtException;
import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.Token;
import io.gravitee.am.gateway.handler.oauth2.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.oauth2.utils.OIDCParameters;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.common.utils.UUID;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenServiceImpl implements TokenService {

    private int accessTokenValiditySeconds = 60 * 60 * 12; // default 12 hours.
    private int refreshTokenValiditySeconds = 60 * 60 * 24 * 30; // default 30 days.

    @Value("${oidc.iss:http://gravitee.am}")
    private String iss;

    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TokenEnhancer tokenEnhancer;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ClientService clientService;

    @Override
    public Maybe<Token> getAccessToken(String token, Client client) {
        return jwtService.decodeAndVerify(token, client)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof JwtException) {
                        return Single.error(new InvalidTokenException(ex.getMessage(), ex));
                    }
                    return Single.error(ex);
                })
                .flatMapMaybe(jwt -> accessTokenRepository.findByToken(jwt.getJti()).map(accessToken -> convertAccessToken(jwt)));
    }

    @Override
    public Maybe<Token> getRefreshToken(String refreshToken, Client client) {
        return jwtService.decodeAndVerify(refreshToken, client)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof JwtException) {
                        return Single.error(new InvalidTokenException(ex.getMessage(), ex));
                    }
                    return Single.error(ex);
                })
                .flatMapMaybe(jwt -> refreshTokenRepository.findByToken(jwt.getJti()).map(refreshToken1 -> convertRefreshToken(jwt)));
    }

    @Override
    public Maybe<Token> introspect(String token) {
        // any client can introspect a token, we first need to decode the token to get the client's certificate to verify the token
        return jwtService.decode(token)
                .flatMapMaybe(jwt -> clientService.findByDomainAndClientId(jwt.getDomain(), jwt.getAud()))
                .switchIfEmpty(Maybe.error(new InvalidTokenException("Invalid or unknown client for this token")))
                .flatMap(client -> getAccessToken(token, client));
    }

    @Override
    public Single<Token> create(OAuth2Request oAuth2Request, Client client, User endUser) {
        // store access token and refresh token (if exits)
        // encode access/refresh token in JWT compact string format
        // convert to access token response format
        return Single.just(oAuth2Request.isSupportRefreshToken())
                .flatMap(supportRefreshToken -> {
                    if (supportRefreshToken) {
                        return storeRefreshToken(client, endUser)
                                .flatMap(refreshToken -> storeAccessToken(oAuth2Request, client, endUser, refreshToken.getToken())
                                        .flatMap(accessToken -> convert(accessToken, refreshToken, client, oAuth2Request)));
                    } else {
                        return storeAccessToken(oAuth2Request, client, endUser, null)
                                .flatMap(accessToken -> convert(accessToken, null, client, oAuth2Request));
                    }
                })
                .flatMap(accessToken1 -> tokenEnhancer.enhance(accessToken1, oAuth2Request, client, endUser));
    }

    @Override
    public Single<Token> refresh(String refreshToken, TokenRequest tokenRequest, Client client) {
        // invalid_grant : The provided authorization grant (e.g., authorization code, resource owner credentials) or refresh token is
        // invalid, expired, revoked or was issued to another client.
        return getRefreshToken(refreshToken, client)
                .switchIfEmpty(Single.error(new InvalidGrantException("Refresh token is invalid")))
                .flatMap(refreshToken1 -> {
                    if (refreshToken1.getExpireAt().before(new Date())) {
                        throw new InvalidGrantException("Refresh token is expired");
                    }
                    if (!refreshToken1.getClientId().equals(tokenRequest.getClientId())) {
                        throw new InvalidGrantException("Refresh token was issued to another client");
                    }

                    // refresh token is used only once
                    return refreshTokenRepository.delete(refreshToken1.getValue())
                            .andThen(Single.just(refreshToken1));
                });
    }

    @Override
    public Completable deleteAccessToken(String accessToken) {
        return accessTokenRepository.delete(accessToken);
    }

    @Override
    public Completable deleteRefreshToken(String refreshToken) {
        return refreshTokenRepository.delete(refreshToken);
    }

    /**
     * Store access token
     * @param oAuth2Request oauth2 token or authorization request
     * @param client oauth2 client
     * @param endUser oauth2 resource owner
     * @param refreshToken refresh token id
     * @return access token
     */
    private Single<io.gravitee.am.repository.oauth2.model.AccessToken> storeAccessToken(OAuth2Request oAuth2Request, Client client, User endUser, String refreshToken) {
        io.gravitee.am.repository.oauth2.model.AccessToken accessToken = new io.gravitee.am.repository.oauth2.model.AccessToken();
        accessToken.setId(UUID.random().toString());
        accessToken.setToken(UUID.random().toString());
        accessToken.setDomain(client.getDomain());
        accessToken.setClient(client.getClientId());
        accessToken.setSubject(endUser != null ? endUser.getId() : null);
        accessToken.setCreatedAt(new Date());
        accessToken.setExpireAt(new Date(System.currentTimeMillis() + (getAccessTokenValiditySeconds(client) * 1000L)));
        // set authorization code
        if (oAuth2Request.getRequestParameters() != null) {
            MultiValueMap<String, String> requestParameters = oAuth2Request.getRequestParameters();
            String authorizationCode = requestParameters.getFirst(OAuth2Constants.CODE);
            if (authorizationCode != null) {
                accessToken.setAuthorizationCode(authorizationCode);
            }
        }
        // set refresh token
        if (refreshToken != null) {
            accessToken.setRefreshToken(refreshToken);
        }
        return accessTokenRepository.create(accessToken);
    }

    /**
     * Store refresh token
     * @param client oauth2 client
     * @param endUser oauth2 resource owner
     * @return refresh token
     */
    private Single<io.gravitee.am.repository.oauth2.model.RefreshToken> storeRefreshToken(Client client, User endUser) {
        io.gravitee.am.repository.oauth2.model.RefreshToken refreshToken = new io.gravitee.am.repository.oauth2.model.RefreshToken();
        refreshToken.setId(UUID.random().toString());
        refreshToken.setToken(UUID.random().toString());
        refreshToken.setDomain(client.getDomain());
        refreshToken.setClient(client.getClientId());
        refreshToken.setSubject(endUser != null ? endUser.getId() : null);
        refreshToken.setCreatedAt(new Date());
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + (getRefreshTokenValiditySeconds(client) * 1000L)));

        return refreshTokenRepository.create(refreshToken);
    }

    /**
     * Convert to access token response format
     * @param accessToken access token (compact JWT format)
     * @param refreshToken refresh token (compact JWT format)
     * @param oAuth2Request oauth2 token or authorization request
     * @return access token response format
     */
    private Single<Token> convert(io.gravitee.am.repository.oauth2.model.AccessToken accessToken, io.gravitee.am.repository.oauth2.model.RefreshToken refreshToken, Client client, OAuth2Request oAuth2Request) {
        return jwtService.encode(convert(accessToken, oAuth2Request), client)
                .flatMap(encodedAccessToken -> {
                    if (refreshToken != null) {
                        return jwtService.encode(convert(refreshToken, oAuth2Request), client)
                                .map(encodedRefreshToken -> convert(accessToken, encodedAccessToken, encodedRefreshToken, oAuth2Request));
                    } else {
                        return Single.just(convert(accessToken, encodedAccessToken, null, oAuth2Request));
                    }
                }); // RSA Signer can be very slow, delegate work to a bounded thread-pool
    }

    /**
     * Convert JWT object to Access Token Response Format after access/refresh token creation
     * @param accessToken access token previously stored
     * @param encodedAccessToken access token JWT compact string format
     * @param encodedRefreshToken refresh token JWT compact string format
     * @param oAuth2Request oauth2 token or authorization request
     * @return Access Token Response Format
     */
    private Token convert(io.gravitee.am.repository.oauth2.model.AccessToken accessToken, String encodedAccessToken, String encodedRefreshToken, OAuth2Request oAuth2Request) {
        AccessToken token = new AccessToken(encodedAccessToken);
        int expiresIn = (accessToken.getExpireAt() != null) ?  (int) ((accessToken.getExpireAt().getTime() - System.currentTimeMillis()) / 1000L) : 0;
        token.setExpiresIn(expiresIn);
        // set scopes
        Set<String> scopes = oAuth2Request.getScopes();
        if (scopes != null && !scopes.isEmpty()) {
            token.setScope(String.join(" ", scopes));
        }
        // set additional information
        if (oAuth2Request.getAdditionalParameters() != null && !oAuth2Request.getAdditionalParameters().isEmpty()) {
            oAuth2Request.getAdditionalParameters().toSingleValueMap().forEach((k, v) -> token.getAdditionalInformation().put(k, v));
        }
        // set refresh token
        token.setRefreshToken(encodedRefreshToken);

        return token;
    }


    /**
     * Convert JWT object to Access Token
     * @param jwt jwt to convert
     * @return access token response format
     */
    private Token convertAccessToken(JWT jwt) {
        AccessToken accessToken = new AccessToken(jwt.getJti());
        return convert(accessToken, jwt);
    }

    /**
     * Convert JWT object to Refresh Token
     * @param jwt jwt to convert
     * @return access token response format
     */
    private Token convertRefreshToken(JWT jwt) {
        RefreshToken refreshToken = new RefreshToken(jwt.getJti());
        return convert(refreshToken, jwt);
    }

    private Token convert(Token token, JWT jwt) {
        token.setClientId(jwt.getAud());
        token.setSubject(jwt.getSub());
        token.setScope(jwt.getScope());
        token.setCreatedAt(new Date(jwt.getIat() * 1000l));
        token.setExpireAt(new Date(jwt.getExp() * 1000l));
        token.setExpiresIn(token.getExpireAt() != null ? Long.valueOf((token.getExpireAt().getTime() - System.currentTimeMillis()) / 1000L).intValue() : 0);

        // set add additional information (currently only claims parameter)
        if (jwt.getClaimsRequestParameter() != null) {
            token.setAdditionalInformation(Collections.singletonMap(Claims.claims, jwt.getClaimsRequestParameter()));
        }
        return token;
    }

    /**
     * Convert access/refresh token to JWT Object
     *
     * @param token access or refresh token
     * @param oAuth2Request oauth2 token or authorization request
     * @return JWT
     */
    private JWT convert(io.gravitee.am.repository.oauth2.model.Token token, OAuth2Request oAuth2Request) {
        JWT jwt = new JWT();
        jwt.setIss(iss);
        jwt.setSub(token.getSubject() != null ? token.getSubject() : token.getClient());
        jwt.setAud(oAuth2Request.getClientId());
        jwt.setDomain(token.getDomain());
        jwt.setIat((token.getCreatedAt() != null) ? token.getCreatedAt().getTime() / 1000l : 0);
        jwt.setExp((token.getExpireAt() != null) ? token.getExpireAt().getTime() / 1000l : 0);
        jwt.setJti(token.getToken());

        // set scopes
        Set<String> scopes = oAuth2Request.getScopes();
        if (scopes != null && !scopes.isEmpty()) {
            jwt.setScope(String.join(" ", scopes));
        }

        // set claims parameter (only for an access token)
        // useful for UserInfo Endpoint to request for specific claims
        MultiValueMap<String, String> requestParameters = oAuth2Request.getRequestParameters();
        if (requestParameters != null && requestParameters.getFirst(OIDCParameters.CLAIMS) != null) {
            if (token instanceof io.gravitee.am.repository.oauth2.model.AccessToken) {
                jwt.setClaimsRequestParameter(requestParameters.getFirst(OIDCParameters.CLAIMS));
            }
        }

        return jwt;
    }

    /**
     * Get access token validity in seconds
     * @param client client which set this option
     * @return access token validity in seconds
     */
    private Integer getAccessTokenValiditySeconds(Client client) {
        int validitySeconds = client.getAccessTokenValiditySeconds();
        return validitySeconds > 0 ? validitySeconds : accessTokenValiditySeconds;
    }

    /**
     * Get refresh token validity in seconds
     * @param client client which set this option
     * @return refresh token validity in seconds
     */
    private Integer getRefreshTokenValiditySeconds(Client client) {
        int validitySeconds = client.getRefreshTokenValiditySeconds();
        return validitySeconds > 0 ? validitySeconds : refreshTokenValiditySeconds;
    }
}
