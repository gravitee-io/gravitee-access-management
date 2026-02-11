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
import io.gravitee.am.common.jwt.CertificateInfo;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.EncodedJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.OrigResourcesUtils;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
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
import io.gravitee.common.util.Maps;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.codegen.annotations.Nullable;
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

    public static final String SIGNING_CERTIFICATE_ID = "SIGNING_CERTIFICATE_ID";
    public static final String SIGNING_CERTIFICATE_NAME = "SIGNING_CERTIFICATE_NAME";

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

    @Override
    public Maybe<Token> getAccessToken(String token, Client client) {
        return jwtService.decodeAndVerify(token, client, ACCESS_TOKEN)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof JWTException) {
                        return Single.error(new InvalidTokenException(ex.getMessage(), ex));
                    }
                    return Single.error(ex);
                })
                .flatMapMaybe(jwt -> accessTokenRepository.findByToken(jwt.getJti()).map(accessToken -> convertAccessToken(jwt, null)));
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
                .flatMapMaybe(jwt -> refreshTokenRepository.findByToken(jwt.getJti()).map(refreshToken1 -> convertRefreshToken(jwt, null)));
    }

    @Override
    public Maybe<Token> introspect(String token, TokenTypeHint hint) {
        switch (hint) {
            case REFRESH_TOKEN:
                return introspectAsRefreshTokenFirst(token, null);
            case ACCESS_TOKEN:
                return introspectAsAccessTokenFirst(token, null);
            default:
                return Maybe.empty();
        }
    }

    @Override
    public Maybe<Token> introspect(String token, TokenTypeHint hint, String callerClientId) {
        switch (hint) {
            case REFRESH_TOKEN:
                return introspectAsRefreshTokenFirst(token, callerClientId);
            case ACCESS_TOKEN:
                return introspectAsAccessTokenFirst(token, callerClientId);
            default:
                return Maybe.empty();
        }
    }

    @Override
    public Maybe<Token> introspect(String token, String callerClientId) {
        return introspectAsAccessTokenFirst(token, callerClientId);
    }

    private Maybe<Token> introspectAsAccessTokenFirst(String token, String callerClientId) {
        return introspectAsAccessToken(token, callerClientId)
                .switchIfEmpty(introspectAsRefreshToken(token, callerClientId));
    }

    private Maybe<Token> introspectAsRefreshTokenFirst(String token, String callerClientId) {
        return introspectAsRefreshToken(token, callerClientId)
                .switchIfEmpty(introspectAsAccessToken(token, callerClientId));
    }

    private Maybe<Token> introspectAsAccessToken(String token, String callerClientId) {
        return introspectionTokenFacade.introspectAccessToken(token, callerClientId)
                .map(result -> convertAccessToken(result.jwt(), result.clientId()));
    }

    private Maybe<Token> introspectAsRefreshToken(String token, String callerClientId) {
        return introspectionTokenFacade.introspectRefreshToken(token, callerClientId)
                .map(result -> convertRefreshToken(result.jwt(), result.clientId()));
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
                                    jwtService.encodeJwt(accessToken, client),
                                    (refreshToken != null ? jwtService.encodeJwt(refreshToken, client).map(Optional::of) : Single.just(Optional.<EncodedJWT>empty())),
                                    (encodedAccessToken, optionalEncodedRefreshToken) -> convert(accessToken, encodedAccessToken, optionalEncodedRefreshToken.orElse(null), oAuth2Request))
                            .flatMap(tokenWithCertInfo -> enhanceToken(oAuth2Request, client, endUser, executionContext, tokenWithCertInfo))
                            .flatMap(tokenWithCertInfo -> storeTokens(accessToken, refreshToken, oAuth2Request, endUser).toSingle(() -> tokenWithCertInfo))
                            .doOnSuccess(tokenWithCertInfo -> auditService.report(buildTokenCreatedAudit(oAuth2Request, client, endUser, accessToken, refreshToken, tokenWithCertInfo)));
                })
                .map(tokenWithCertificateInfo -> tokenWithCertificateInfo.token)
                .doOnError(error -> auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class).tokenActor(client).tokenTarget(endUser).throwable(error)));
    }

    private Single<TokenWithCertificateInfo> enhanceToken(OAuth2Request oAuth2Request, Client client, User endUser, ExecutionContext executionContext, TokenWithCertificateInfo tokenWithCertificateInfo) {
        return tokenEnhancer.enhance(tokenWithCertificateInfo.token, oAuth2Request, client, endUser, executionContext).map(token -> new TokenWithCertificateInfo(token, tokenWithCertificateInfo.certificateInfo));
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
            return persistAccessToken.andThen(tokenManager.storeRefreshToken(convert(refreshToken, user, oAuth2Request.getClientId())));
        }
        return persistAccessToken;
    }

    private io.gravitee.am.repository.oauth2.model.AccessToken convert(JWT token, JWT refreshToken, OAuth2Request oAuth2Request, User user) {
        io.gravitee.am.repository.oauth2.model.AccessToken accessToken = convertCommon(new io.gravitee.am.repository.oauth2.model.AccessToken(), token, user, oAuth2Request.getClientId());
        // set authorization code
        accessToken.setAuthorizationCode(oAuth2Request.parameters() != null ? oAuth2Request.parameters().getFirst(io.gravitee.am.common.oauth2.Parameters.CODE) : null);
        // set refresh token
        accessToken.setRefreshToken(refreshToken != null ? refreshToken.getJti() : null);
        return accessToken;
    }

    private io.gravitee.am.repository.oauth2.model.RefreshToken convert(JWT token, User user, String clientId) {
        return convertCommon(new io.gravitee.am.repository.oauth2.model.RefreshToken(), token, user, clientId);
    }

    private <T extends io.gravitee.am.repository.oauth2.model.Token> T convertCommon(T newToken, JWT sourceToken, User user, String clientId) {
        newToken.setId(RandomString.generate());
        newToken.setToken(sourceToken.getJti());
        newToken.setDomain(sourceToken.getDomain());
        newToken.setClient(clientId);
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
     * @return object containing: Access Token Response Format and Certificate Info
     */
    private TokenWithCertificateInfo convert(JWT accessToken, EncodedJWT encodedAccessToken, @Nullable EncodedJWT encodedRefreshToken, OAuth2Request oAuth2Request) {
        AccessToken token = new AccessToken(encodedAccessToken.encodedToken());
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
        Optional.ofNullable(encodedRefreshToken).map(EncodedJWT::encodedToken).ifPresent(token::setRefreshToken);
        return new TokenWithCertificateInfo(token, encodedAccessToken.certificateInfo());
    }

    private record TokenWithCertificateInfo(
            Token token,
            CertificateInfo certificateInfo
    ) {}


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
        token.setClientId(clientId != null ? clientId : jwt.getAud());
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

        // Apply resource to aud
        setResources(request, jwt);

        return jwt;
    }

    private void setResources(OAuth2Request request, JWT jwt) {
        Set<String> resource = request.getResources();
        if (resource == null || resource.isEmpty()) {
            return;
        }

        var jsonArray = new JSONArray();
        jsonArray.addAll(resource);

        logger.debug("resources: {}, JTI: {}, client ID:{}", jsonArray, jwt.getJti(), request.getClientId());

        jwt.put(Claims.AUD, jsonArray);
    }

    private JWT createRefreshTokenJWT(OAuth2Request request, Client client, User user, JWT accessToken) {
        JWT jwt = createJWT(request, client, user);
        // set exp claim
        jwt.setExp(Instant.ofEpochSecond(jwt.getIat()).plusSeconds(client.getRefreshTokenValiditySeconds()).getEpochSecond());
        // set custom claims from the current access token
        Map<String, Object> customClaims = new HashMap<>(accessToken);
        Claims.getAllClaims().forEach(customClaims::remove);
        jwt.putAll(customClaims);
        
        // Store originally granted resources in refresh token for RFC 8707 compliance
        setOrigResourcesClaim(jwt, request);

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
        jwt.setAud(oAuth2Request.getClientId());
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

    /**
     * Sets the orig_resources claim in the refresh token JWT for RFC 8707 compliance.
     * - If this is a refresh flow, preserves the orig_resources from the previous refresh token
     * - Otherwise (initial issuance), uses the current request resources (from authorization)
     *
     * @param jwt the JWT to add the claim to
     * @param request the OAuth2 request containing resources
     */
    private void setOrigResourcesClaim(JWT jwt, OAuth2Request request) {
        Set<String> origResources = determineOriginalResources(request);

        if (!origResources.isEmpty()) {
            var jsonArray = new JSONArray();
            jsonArray.addAll(origResources);
            jwt.put(Claims.ORIG_RESOURCES, jsonArray);
            logger.debug("Refresh token {} stored: {}, JTI: {}", Claims.ORIG_RESOURCES, jsonArray, jwt.getJti());
        } else {
            logger.debug("No {} to store in refresh token, JTI: {}", Claims.ORIG_RESOURCES, jwt.getJti());
        }
    }

    /**
     * Determines the original resources to persist into a refresh token, with early returns for clarity:
     * 1) If present, reuse orig_resources from the previous refresh token (refresh flow)
     * 2) Else, use original authorization resources captured on the request (authorization code flow)
     * 3) Else, fall back to current request resources
     */
    private Set<String> determineOriginalResources(OAuth2Request request) {
        // 1) Try to reuse from previous refresh token
        Map<String, Object> previousRefreshToken = request.getRefreshToken();
        Set<String> preserved = OrigResourcesUtils.extractOrigResources(previousRefreshToken);
        if (preserved != null && !preserved.isEmpty()) {
            return preserved;
        }

        // 2) Use original authorization resources captured on the request
        Set<String> originalAuthorizationResources = request.getOriginalAuthorizationResources();
        if (originalAuthorizationResources != null && !originalAuthorizationResources.isEmpty()) {
            logger.debug("Using original authorization resources from request field: {}", originalAuthorizationResources);
            return new java.util.LinkedHashSet<>(originalAuthorizationResources);
        }

        // 3) Fall back to current request resources
        Set<String> originalResources = request.getResources();
        logger.debug("Falling back to request resources: {}", originalResources);
        return originalResources == null ? java.util.Set.of() : new java.util.LinkedHashSet<>(originalResources);
    }

    private ClientTokenAuditBuilder buildTokenCreatedAudit(OAuth2Request oAuth2Request, Client client, User endUser, 
                                                           JWT accessToken, JWT refreshToken, TokenWithCertificateInfo tokenWithCertInfo) {
        Token enhancedToken = tokenWithCertInfo.token;

        return AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .accessToken(accessToken.getJti())
                .refreshToken(refreshToken != null ? refreshToken.getJti() : null)
                .idTokenFor(enhancedToken.getAdditionalInformation().getOrDefault("id_token", null) != null ? endUser : null)
                .tokenActor(client)
                .withParams(() -> buildAuditParams(oAuth2Request, tokenWithCertInfo.certificateInfo))
                .tokenTarget(endUser)
                .accessTokenSubject(enhancedToken.getSubject());
    }

    private Map<String, Object> buildAuditParams(OAuth2Request oAuth2Request, CertificateInfo certificateInfo) {
        var params = new HashMap<String, Object>();
        params.put(io.gravitee.am.common.oauth2.Parameters.GRANT_TYPE, oAuth2Request.getGrantType());
        params.put(io.gravitee.am.common.oauth2.Parameters.RESPONSE_TYPE, oAuth2Request.getResponseType());
        
        if (!isEmpty(oAuth2Request.getScopes())) {
            params.put(io.gravitee.am.common.oauth2.Parameters.SCOPE, String.join(" ", oAuth2Request.getScopes()));
        }
        
        if (!isEmpty(oAuth2Request.getResources())) {
            params.put(io.gravitee.am.common.oauth2.Parameters.RESOURCE, String.join(" ", oAuth2Request.getResources()));
        }

        if (certificateInfo != null) {
            params.put(SIGNING_CERTIFICATE_ID, certificateInfo.certificateId());
            params.put(SIGNING_CERTIFICATE_NAME, certificateInfo.certificateAlias());
        }
        
        return params;
    }
}
