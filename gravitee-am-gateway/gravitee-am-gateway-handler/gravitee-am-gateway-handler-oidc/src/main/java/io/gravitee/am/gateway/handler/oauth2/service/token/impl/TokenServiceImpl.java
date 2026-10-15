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
package io.gravitee.am.gateway.handler.oauth2.service.token.impl;

import io.gravitee.am.common.exception.jwt.JWTException;
import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.oauth2.impl.IntrospectionAnyTokenService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.repository.oauth2.api.BackwardCompatibleTokenRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.ACCESS_TOKEN;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.REFRESH_TOKEN;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class TokenServiceImpl implements TokenService {
    private static final String PERMISSIONS = "permissions";

    @Autowired
    private BackwardCompatibleTokenRepository tokenRepository;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private IntrospectionAnyTokenService introspectionTokenService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private TokenMinterFacade tokenMinter;

    @Override
    public Maybe<Token> getAccessToken(String token, Client client) {
        return jwtService.decodeAndVerify(token, client, ACCESS_TOKEN)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof JWTException) {
                        return Single.error(new InvalidTokenException(ex.getMessage(), ex));
                    }
                    return Single.error(ex);
                })
                .flatMapMaybe(jwt -> tokenRepository.findAccessTokenByJti(jwt.getJti()).map(accessToken -> convertAccessToken(jwt, accessToken.getClient())));
    }

    @Override
    public Maybe<Token> getRefreshToken(String refreshToken, Client client) {
        return jwtService.decodeAndVerify(refreshToken, client, REFRESH_TOKEN)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof JWTException) {
                        return Single.error(new InvalidTokenException(ex.getMessage(), ex));
                    }
                    return Single.error(ex);
                })
                .flatMapMaybe(jwt -> tokenRepository.findRefreshTokenByJti(jwt.getJti()).map(storedRefreshToken -> convertRefreshToken(jwt, storedRefreshToken.getClient())));
    }

    @Override
    public Maybe<String> getRefreshTokenJkt(String refreshToken, Client client) {
        return jwtService.decodeAndVerify(refreshToken, client, REFRESH_TOKEN)
                .onErrorComplete()
                .mapOptional(jwt -> Optional.ofNullable(jwt.getDPoPConfirmationThumbprint()));
    }

    @Override
    public Maybe<Token> introspect(String token, TokenTypeHint hint) {
        return introspect(token, hint, null);
    }

    @Override
    public Maybe<Token> introspect(String token, TokenTypeHint hint, String callerClientId) {
        return switch (hint) {
            case REFRESH_TOKEN -> introspectAs(token, REFRESH_TOKEN, callerClientId);
            case ACCESS_TOKEN -> introspectAs(token, ACCESS_TOKEN, callerClientId);
            default -> Maybe.empty();
        };
    }

    @Override
    public Maybe<Token> introspect(String token, String callerClientId) {
        return introspectAs(token, ACCESS_TOKEN, callerClientId);
    }

    private Maybe<Token> introspectAs(String token, JWTService.TokenType hint, String callerClientId) {
        return introspectionTokenService.introspect(token, hint, callerClientId)
                .onErrorResumeWith(Maybe.empty())
                .map(result -> result.tokenType() == REFRESH_TOKEN
                        ? convertRefreshToken(result.jwtWithWithheldClaims(), result.clientId())
                        : convertAccessToken(result.jwtWithWithheldClaims(), result.clientId()));
    }

    @Override
    public Single<Token> create(OAuth2Request oAuth2Request, Client client, User endUser) {
        return tokenMinter.mint(oAuth2Request, client, endUser);
    }

    @Override
    public Single<Token> refresh(String refreshToken, TokenRequest tokenRequest, Client client) {
        // invalid_grant : The provided authorization grant (e.g., authorization code, resource owner credentials) or refresh token is
        // invalid, expired, revoked or was issued to another client.
        return getRefreshToken(refreshToken, client)
                .switchIfEmpty(Single.error(new InvalidGrantException("Refresh token is invalid")))
                .onErrorResumeNext(error -> {
                    if (error instanceof InvalidTokenException) {
                        return Single.error(new InvalidGrantException("Refresh token is invalid"));
                    }
                    return Single.error(error);
                })
                .flatMap(refreshToken1 -> {
                    if (refreshToken1.getExpireAt().before(new Date())) {
                        return Single.error(new InvalidGrantException("Refresh token is expired"));
                    }
                    if (!refreshToken1.getClientId().equals(tokenRequest.getClientId())) {
                        return Single.error(new InvalidGrantException("Refresh token was issued to another client"));
                    }
                    // Propagate UMA 2.0 permissions
                    if (refreshToken1.getAdditionalInformation().get(PERMISSIONS) != null) {
                        tokenRequest.setPermissions((List<PermissionRequest>) refreshToken1.getAdditionalInformation().get(PERMISSIONS));
                    }

                    // if client has disabled refresh token rotation, do not remove the refresh token
                    if (client.isDisableRefreshTokenRotation()) {
                        return Single.just(refreshToken1);
                    }

                    // else, refresh token is used only once
                    return tokenRepository.deleteByJti(refreshToken1.getValue())
                            .andThen(Single.just(refreshToken1));
                })
                .doOnEvent((token, error) -> auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class)
                                .refreshToken(token != null ? token.getValue() : null)
                                .tokenActor(client)
                                .revoked("Refresh token used to generate new token")
                                .throwable(error)));
    }

    @Override
    public Completable deleteAccessToken(String accessToken) {
        return tokenRepository.deleteByJti(accessToken);
    }

    @Override
    public Completable deleteRefreshToken(String refreshToken) {
        return tokenRepository.deleteByJti(refreshToken);
    }


    /**
     * Convert JWT object to Access Token
     * @param jwt jwt to convert
     * @param clientId client id, if known
     * @return access token response format
     */
    private Token convertAccessToken(JWT jwt, String clientId) {
        AccessToken accessToken = new AccessToken(jwt.getJti());
        if (jwt.getConfirmationMethod() != null) {
            accessToken.setConfirmationMethod((Map) jwt.getConfirmationMethod());
        }
        return convert(accessToken, jwt, clientId);
    }

    /**
     * Convert JWT object to Refresh Token
     * @param jwt jwt to convert
     * @param clientId client id, if known
     * @return access token response format
     */
    private Token convertRefreshToken(JWT jwt, String clientId) {
        RefreshToken refreshToken = new RefreshToken(jwt.getJti());
        return convert(refreshToken, jwt, clientId);
    }

    private Token convert(Token token, JWT jwt, String clientId) {
        token.setClientId(resolveClientId(jwt, clientId));
        token.setSubject(jwt.getSub());
        token.setScope(jwt.getScope());
        token.setCreatedAt(new Date(jwt.getIat() * 1000L));
        token.setExpireAt(new Date(jwt.getExp() * 1000L));
        token.setExpiresIn(token.getExpireAt() != null ? Long.valueOf((token.getExpireAt().getTime() - System.currentTimeMillis()) / 1000L) : 0);
        token.setAdditionalInformation(jwt);
        return token;
    }

    // RFC 8707 access tokens put the resource URI in aud.
    private static String resolveClientId(JWT jwt, String storedClientId) {
        if (storedClientId != null) {
            return storedClientId;
        }
        return jwt.get(Claims.CLIENT_ID) instanceof String clientIdClaim ? clientIdClaim : jwt.getAud();
    }

}
