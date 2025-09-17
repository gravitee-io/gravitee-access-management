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
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.common.client.ClientManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenFacade;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenManager;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.am.service.reporter.builder.TokenDelegationAuditBuilder;
import io.gravitee.common.util.Maps;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.Setter;
import net.minidev.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.gravitee.am.common.oidc.ResponseType.ID_TOKEN;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.ACCESS_TOKEN;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.REFRESH_TOKEN;
import static org.springframework.util.ObjectUtils.isEmpty;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenServiceImpl implements TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenServiceImpl.class);
    private static final String PERMISSIONS = "permissions";

    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TokenEnhancer tokenEnhancer;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Autowired
    private ExecutionContextFactory executionContextFactory;

    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private IntrospectionTokenFacade introspectionTokenFacade;

    @Autowired
    private AuditService auditService;

    @Autowired
    private SubjectManager subjectManager;

    @Setter
    @Value("${handlers.oauth2.response.strict:false}")
    private boolean strictResponse = false;

    @Autowired
    private ClientSyncService clientManager;

    @Override
    public Maybe<Token> getAccessToken(String token, Client client) {
        return jwtService.decodeAndVerify(token, client, ACCESS_TOKEN)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof JWTException) {
                        return Single.error(new InvalidTokenException(ex.getMessage(), ex));
                    }
                    return Single.error(ex);
                })
                .flatMapMaybe(jwt -> accessTokenRepository.findByToken(jwt.getJti()).map(accessToken -> convertAccessToken(jwt)));
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
                .flatMapMaybe(jwt -> refreshTokenRepository.findByToken(jwt.getJti()).map(refreshToken1 -> convertRefreshToken(jwt)));
    }

    @Override
    public Maybe<Token> introspect(String token) {
        return introspectionTokenFacade.introspectAccessToken(token)
                .map(this::convertAccessToken)
                .switchIfEmpty(introspectionTokenFacade.introspectRefreshToken(token).map(this::convertRefreshToken));
    }

    @Override
    public Maybe<Token> introspect(String token, TokenTypeHint hint) {
        switch (hint) {
            case REFRESH_TOKEN:
                return introspectionTokenFacade.introspectRefreshToken(token)
                        .map(this::convertRefreshToken)
                        .switchIfEmpty(introspectionTokenFacade.introspectAccessToken(token).map(this::convertAccessToken));
            case ACCESS_TOKEN:
                return introspect(token);
            default:
                return Maybe.empty();
        }
    }

    @Override
    public Single<Token> create(OAuth2Request oAuth2Request, Client client, User endUser) {
        // create execution context
        return Single.fromCallable(() -> createExecutionContext(oAuth2Request, client, endUser))
                .flatMap(executionContext -> {
                    // create JWT access token
                    JWT accessToken = createAccessTokenJWT(oAuth2Request, client, endUser, executionContext);
                    // create JWT refresh token
                    JWT refreshToken = oAuth2Request.isSupportRefreshToken() ? createRefreshTokenJWT(oAuth2Request, client, endUser, accessToken) : null;
                    // encode and sign JWT tokens
                    // and create token response (+ enhance information)
                    return Single.zip(
                                    jwtService.encode(accessToken, client),
                                    (refreshToken != null ? jwtService.encode(refreshToken, client).map(Optional::of) : Single.just(Optional.<String>empty())),
                                    (encodedAccessToken, optionalEncodedRefreshToken) -> convert(accessToken, encodedAccessToken, optionalEncodedRefreshToken.orElse(null), oAuth2Request))
                            .flatMap(accessToken1 -> tokenEnhancer.enhance(accessToken1, oAuth2Request, client, endUser, executionContext))
                            .flatMap(enhancedToken -> storeTokens(accessToken, refreshToken, oAuth2Request, endUser).toSingle(() -> enhancedToken))
                            .doOnSuccess(enhancedToken -> {
                                auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class)
                                    .accessToken(accessToken.getJti())
                                    .refreshToken(refreshToken != null ? refreshToken.getJti() : null)
                                    .idTokenFor(enhancedToken.getAdditionalInformation().getOrDefault("id_token", null) != null ? endUser : null)
                                    .tokenActor(client)
                                    .withParams(() -> {
                                        var params = new HashMap<String, Object>();
                                        params.put(io.gravitee.am.common.oauth2.Parameters.GRANT_TYPE, oAuth2Request.getGrantType());
                                        params.put(io.gravitee.am.common.oauth2.Parameters.RESPONSE_TYPE, oAuth2Request.getResponseType());
                                        if (!isEmpty(oAuth2Request.getScopes())) {
                                            params.put(io.gravitee.am.common.oauth2.Parameters.SCOPE, String.join(" ", oAuth2Request.getScopes()));
                                        }
                                        return params;
                                    })
                                    .tokenTarget(endUser)
                                    .accessTokenSubject(enhancedToken.getSubject()));

                                if (accessToken.get("act") != null) {
                                    clientManager.findByClientId((String)((Map)accessToken.get("act")).get("sub"))
                                                    .subscribe(agent ->
                                    auditService.report(AuditBuilder.builder(TokenDelegationAuditBuilder.class)
                                            .accessToken(accessToken.getJti())
                                            .refreshToken(refreshToken != null ? refreshToken.getJti() : null)
                                            .idTokenFor(enhancedToken.getAdditionalInformation().getOrDefault("id_token", null) != null ? endUser : null)
                                            .tokenActor(endUser)
                                            .withParams(() -> {
                                                var params = new HashMap<String, Object>();
                                                params.put(io.gravitee.am.common.oauth2.Parameters.GRANT_TYPE, oAuth2Request.getGrantType());
                                                params.put(io.gravitee.am.common.oauth2.Parameters.RESPONSE_TYPE, oAuth2Request.getResponseType());
                                                if (!isEmpty(oAuth2Request.getScopes())) {
                                                    params.put(io.gravitee.am.common.oauth2.Parameters.SCOPE, String.join(" ", oAuth2Request.getScopes()));
                                                }
                                                return params;
                                            })
                                            .agent(agent)), error -> logger.error("Unable to report token delegation audit", error));
                                }

                            });
                })
                .doOnError(error -> auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class).tokenActor(client).tokenTarget(endUser).throwable(error)));
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
                        tokenRequest.setPermissions((List<PermissionRequest>)refreshToken1.getAdditionalInformation().get(PERMISSIONS));
                    }

                    // if client has disabled refresh token rotation, do not remove the refresh token
                    if (client.isDisableRefreshTokenRotation()) {
                        return Single.just(refreshToken1);
                    }

                    // else, refresh token is used only once
                    return refreshTokenRepository.delete(refreshToken1.getValue())
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
        return accessTokenRepository.delete(accessToken);
    }

    @Override
    public Completable deleteRefreshToken(String refreshToken) {
        return refreshTokenRepository.delete(refreshToken);
    }

    private Completable storeTokens(JWT accessToken, JWT refreshToken, OAuth2Request oAuth2Request, User user) {
        // store access token
        final Completable persistAccessToken = tokenManager.storeAccessToken(convert(accessToken, refreshToken,  oAuth2Request, user));
        // store refresh token (if exists)
        if (refreshToken != null) {
            return persistAccessToken.andThen(tokenManager.storeRefreshToken(convert(refreshToken, user)));
        }
        return persistAccessToken;
    }

    private io.gravitee.am.repository.oauth2.model.AccessToken convert(JWT token, JWT refreshToken, OAuth2Request oAuth2Request, User user) {
        io.gravitee.am.repository.oauth2.model.AccessToken accessToken = convertCommon(new io.gravitee.am.repository.oauth2.model.AccessToken(), token, user);
        // set authorization code
        accessToken.setAuthorizationCode(oAuth2Request.parameters() != null ? oAuth2Request.parameters().getFirst(io.gravitee.am.common.oauth2.Parameters.CODE) : null);
        // set refresh token
        accessToken.setRefreshToken(refreshToken != null ? refreshToken.getJti() : null);
        return accessToken;
    }

    private io.gravitee.am.repository.oauth2.model.RefreshToken convert(JWT token, User user) {
        return convertCommon(new io.gravitee.am.repository.oauth2.model.RefreshToken(), token, user);
    }

    private <T extends io.gravitee.am.repository.oauth2.model.Token> T convertCommon(T newToken, JWT sourceToken, User user) {
        newToken.setId(RandomString.generate());
        newToken.setToken(sourceToken.getJti());
        newToken.setDomain(sourceToken.getDomain());
        newToken.setClient(sourceToken.getAud());
        // keep reference to userId in the storage, only outside world has to see the sub which maybe based on source+extId
        newToken.setSubject(user == null ? sourceToken.getSub() : user.getId());
        newToken.setCreatedAt(new Date(sourceToken.getIat() * 1000));
        newToken.setExpireAt(new Date(sourceToken.getExp() * 1000));
        return newToken;
    }

    /**
     * Convert JWT object to Access Token Response Format
     * @param accessToken access token
     * @param encodedAccessToken access token JWT compact string format
     * @param encodedRefreshToken refresh token JWT compact string format
     * @param oAuth2Request oauth2 token or authorization request
     * @return Access Token Response Format
     */
    private Token convert(JWT accessToken, String encodedAccessToken, String encodedRefreshToken, OAuth2Request oAuth2Request) {
        AccessToken token = new AccessToken(encodedAccessToken);
        token.setSubject(accessToken.getSub());
        token.setExpiresIn(Instant.ofEpochSecond(accessToken.getExp()).minusMillis(System.currentTimeMillis()).getEpochSecond());
        token.setScope(accessToken.getScope());
        // set additional information
        if (!strictResponse && oAuth2Request.getAdditionalParameters() != null && !oAuth2Request.getAdditionalParameters().isEmpty()) {
            oAuth2Request.getAdditionalParameters().toSingleValueMap().entrySet().stream()
                    .filter(e -> !Token.getStandardParameters().contains(e.getKey()) && !e.getKey().equals(ID_TOKEN))
                    .forEach(e -> token.getAdditionalInformation().put(e.getKey(), e.getValue()));
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
        if (jwt.getConfirmationMethod() != null) {
            accessToken.setConfirmationMethod((Map) jwt.getConfirmationMethod());
        }
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
        token.setCreatedAt(new Date(jwt.getIat() * 1000L));
        token.setExpireAt(new Date(jwt.getExp() * 1000L));
        token.setExpiresIn(token.getExpireAt() != null ? Long.valueOf((token.getExpireAt().getTime() - System.currentTimeMillis()) / 1000L) : 0);
        token.setAdditionalInformation(jwt);
        return token;
    }

    private JWT createAccessTokenJWT(OAuth2Request request, Client client, User user, ExecutionContext executionContext) {
        JWT jwt = createJWT(request, client, user);
        // set exp claim
        jwt.setExp(Instant.ofEpochSecond(jwt.getIat()).plusSeconds(client.getAccessTokenValiditySeconds()).getEpochSecond());

        final String cnfValue = request.getConfirmationMethodX5S256();
        if (cnfValue != null) {
            jwt.setConfirmationMethod(Maps.<String, Object>builder().put(JWT.CONFIRMATION_METHOD_X509_THUMBPRINT, cnfValue).build());
        }
        // set claims parameter (only for an access token)
        // useful for UserInfo Endpoint to request for specific claims
        MultiValueMap<String, String> requestParameters = request.parameters();
        if (requestParameters != null && requestParameters.getFirst(Parameters.CLAIMS) != null) {
            jwt.setClaimsRequestParameter(requestParameters.getFirst(Parameters.CLAIMS));
        }

        // set custom claims
        enhanceJWT(jwt, client.getTokenCustomClaims(), TokenTypeHint.ACCESS_TOKEN, executionContext);



        return jwt;
    }

    private JWT createRefreshTokenJWT(OAuth2Request request, Client client, User user, JWT accessToken) {
        JWT jwt = createJWT(request, client, user);
        // set exp claim
        jwt.setExp(Instant.ofEpochSecond(jwt.getIat()).plusSeconds(client.getRefreshTokenValiditySeconds()).getEpochSecond());
        // set custom claims from the current access token
        Map<String, Object> customClaims = new HashMap<>(accessToken);
        Claims.getAllClaims().forEach(customClaims::remove);
        jwt.putAll(customClaims);

        return jwt;
    }


    private JWT createJWT(OAuth2Request oAuth2Request, Client client, User user) {
        JWT jwt = new JWT();
        jwt.setIss(openIDDiscoveryService.getIssuer(oAuth2Request.getOrigin()));
        if (oAuth2Request.isClientOnly()) {
            jwt.setSub(client.getClientId());
        } else {
            subjectManager.updateJWT(jwt, user);
        }
        jwt.setAud(oAuth2Request.getClientId()); // aud maybe resource server uri...
        jwt.setDomain(client.getDomain());
        jwt.setIat(Instant.now().getEpochSecond());
        jwt.setJti(SecureRandomString.generate());

        // set scopes
        Set<String> scopes = oAuth2Request.getScopes();
        if (scopes != null && !scopes.isEmpty()) {
            jwt.setScope(String.join(" ", scopes));
        }

        // set permissions (UMA 2.0)
        List<PermissionRequest> permissions = oAuth2Request.getPermissions();
        if (permissions != null && !permissions.isEmpty()) {
            jwt.put(PERMISSIONS, permissions);
        }

        if (oAuth2Request.parameters().containsKey("requested_actor")) {
            jwt.put("act",Map.of("sub", oAuth2Request.parameters().getFirst("requested_actor")) );
        }

        return jwt;
    }

    private void enhanceJWT(JWT jwt, List<TokenClaim> customClaims, TokenTypeHint tokenTypeHint, ExecutionContext executionContext) {
        if (customClaims != null && !customClaims.isEmpty()) {
            customClaims
                    .stream()
                    .filter(tokenClaim -> tokenTypeHint.equals(tokenClaim.getTokenType()))
                    .forEach(tokenClaim -> {
                        try {
                            String claimName = tokenClaim.getClaimName();
                            String claimExpression = tokenClaim.getClaimValue();
                            Object extValue = (claimExpression != null) ? executionContext.getTemplateEngine().getValue(claimExpression, Object.class) : null;
                            if (extValue != null) {
                                if (Claims.AUD.equals(claimName) && (extValue instanceof String[] || extValue instanceof List)) {
                                    var audiences = new LinkedHashSet<>();
                                    audiences.add(jwt.getAud()); // make sure the client_id is the first entry of the aud array
                                    audiences.addAll(extValue instanceof List ? (List) extValue : List.of((String[]) extValue)); // Set will remove duplicate client_id if any
                                    var jsonArray = new JSONArray();
                                    jsonArray.addAll(audiences);
                                    jwt.put(claimName, jsonArray);
                                } else {
                                    jwt.put(claimName, extValue);
                                }
                            }
                        } catch (Exception ex) {
                            logger.debug("An error occurs while parsing expression language : {}", tokenClaim.getClaimValue(), ex);
                        }
                    });
        }
    }

    private ExecutionContext createExecutionContext(OAuth2Request request, Client client, User user) {
        ExecutionContext simpleExecutionContext = new SimpleExecutionContext(request, null);
        ExecutionContext executionContext = executionContextFactory.create(simpleExecutionContext);

        // put authorization request in context
        // AM-3137 => clean OAuth 2.0 request
        OAuth2Request safeRequest = new OAuth2Request(request);
        safeRequest.setExecutionContext(null);
        safeRequest.setHttpResponse(null);
        if (request.getResponseType() != null && !request.getResponseType().isEmpty()) {
            executionContext.setAttribute("authorizationRequest", safeRequest);
        } else {
            executionContext.setAttribute("tokenRequest", safeRequest);
        }
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
