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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.CertificateInfo;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.ClientProfile;
import io.gravitee.am.common.jwt.EncodedJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.OrigResourcesUtils;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.utils.Tuple;
import io.gravitee.am.gateway.handler.oauth2.service.el.ExecutionContextTokenEnhancer;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2RequestParams;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.repository.oauth2.api.BackwardCompatibleTokenRepository;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.common.util.Maps;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import lombok.Setter;
import net.minidev.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.common.jwt.Claims.*;
import static io.gravitee.am.common.oauth2.Parameters.RESOURCE;
import static io.gravitee.am.common.oidc.ResponseType.ID_TOKEN;
import static io.gravitee.am.common.utils.ConstantKeys.DPOP_AUTH_SCHEME;
import static java.util.stream.Stream.concat;

@CustomLog
public final class AccessTokenMinter extends BaseTokenMinter {
    private static final String PERMISSIONS = "permissions";
    private static final String AUTHORIZATION_DETAILS = "authorization_details";

    // gis, domain and claims_request_parameter are internal claims the gateway reads back from the access token
    private static final Set<String> LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS = Set.of(
            ISS, SUB, EXP, IAT, JTI, SCOPE, CLIENT_ID, AUD,
            CNF, ACT, CLIENT_PROFILE, AUTHORIZATION_DETAILS, PERMISSIONS,
            GIO_INTERNAL_SUB, DOMAIN, CLAIMS);

    private static final Set<String> LIGHTWEIGHT_REFRESH_TOKEN_CLAIMS = concat(
            LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS.stream(),
            Stream.of(Claims.ORIG_RESOURCES)).collect(Collectors.toUnmodifiableSet());

    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {};


    @Autowired
    OpenIDDiscoveryService openIDDiscoveryService;

    @Autowired
    JWTService jwtService;

    @Autowired
    SubjectManager subjectManager;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    BackwardCompatibleTokenRepository tokenRepository;

    @Autowired
    TokenEnhancer tokenEnhancer;

    @Setter
    @Value("${handlers.oauth2.response.strict:false}")
    private boolean strictResponse = false;

    @Override
    protected Single<MintedToken> mint(OAuth2Request oAuth2Request, Client client, User endUser, ExecutionContext executionContext) {
        return Single.fromCallable(() -> createAccessTokenJWT(oAuth2Request, client, endUser, executionContext))
                .map(accessToken -> oAuth2Request.isSupportRefreshToken() ?
                        Tuple.of(accessToken, createRefreshTokenJWT(oAuth2Request, client, endUser, accessToken)) :
                        Tuple.<JWT, JWT>of(accessToken, null))
                .flatMap(tokens -> {
                    var accessToken = tokens.getT1();
                    var refreshToken = tokens.getT2();

                    boolean lightweightJwt = client.isLightweightJwtEnabled();
                    Map<String, Object> accessTokenWithheldClaims = lightweightJwt ? withholdAccessTokenClaims(client, accessToken) : null;
                    Map<String, Object> refreshTokenWithheldClaims = lightweightJwt && refreshToken != null ? withholdRefreshTokenClaims(client, refreshToken) : null;

                    return Single.zip(
                                    jwtService.encodeJwt(accessToken, client),
                                    (refreshToken != null ? jwtService.encodeJwt(refreshToken, client).map(Optional::of) : Single.just(Optional.<EncodedJWT>empty())),
                                    (encodedAccessToken, optionalEncodedRefreshToken) -> convert(accessToken, encodedAccessToken, optionalEncodedRefreshToken.orElse(null), oAuth2Request))
                            .flatMap(tokenWithCertInfo -> enhanceToken(oAuth2Request, client, endUser, executionContext, tokenWithCertInfo))
                            .flatMap(tokenWithCertInfo -> storeTokens(accessToken, accessTokenWithheldClaims, refreshToken, refreshTokenWithheldClaims, oAuth2Request, endUser)
                                    .toSingle(() -> new MintedToken(tokenWithCertInfo.token(),
                                            buildTokenCreatedAudit(oAuth2Request, client, endUser, accessToken, refreshToken, tokenWithCertInfo))));
                });
    }

    private JWT createAccessTokenJWT(OAuth2Request request, Client client, User user, ExecutionContext executionContext) {
        JWT jwt = createJWT(request, client, user);

        long defaultExpiration = Instant.ofEpochSecond(jwt.getIat())
                .plusSeconds(client.getAccessTokenValiditySeconds())
                .getEpochSecond();
        long exp = defaultExpiration;

        // Token Exchange (RFC 8693) - do not exceed subject token expiration
        if (request.getExchangeExpiration() != null) {
            long subjectExpiration = request.getExchangeExpiration().toInstant().getEpochSecond();
            exp = Math.min(defaultExpiration, subjectExpiration);
        }

        jwt.setExp(exp);

        jwt.put(Claims.CLIENT_ID, request.getClientId());
        final String cnfValue = request.getConfirmationMethodX5S256();
        if (cnfValue != null) {
            jwt.setConfirmationMethod(Maps.<String, Object>builder().put(JWT.CONFIRMATION_METHOD_X509_THUMBPRINT, cnfValue).build());
        }
        final String dpopJkt = request.getConfirmationMethodJkt();
        if (dpopJkt != null) {
            Map<String, Object> cnf = jwt.getConfirmationMethod() instanceof Map<?, ?> existing
                    ? new HashMap<>((Map<String, Object>) existing)
                    : new HashMap<>();
            cnf.put(JWT.CONFIRMATION_METHOD_JWK_THUMBPRINT, dpopJkt);
            jwt.setConfirmationMethod(cnf);
        }
        // set claims parameter (only for an access token)
        // useful for UserInfo Endpoint to request for specific claims
        MultiValueMap<String, String> requestParameters = request.parameters();
        if (requestParameters != null && requestParameters.getFirst(Parameters.CLAIMS) != null) {
            jwt.setClaimsRequestParameter(requestParameters.getFirst(Parameters.CLAIMS));
        }

        // set custom claims
        enhanceJWT(jwt, client.getTokenCustomClaims(), TokenTypeHint.ACCESS_TOKEN, executionContext);

        // Token Exchange (RFC 8693) - set "act" claim for delegation scenarios
        if (request.isDelegation() && request.getActClaim() != null) {
            jwt.put(Claims.ACT, request.getActClaim());
        }

        // Agent application - inject "act" claim (actor = agent instance or blueprint client_id)
        // and tag the actor with sub_profile so downstream consumers can reason about the chain.
        // For user-bound kinds (USER_EMBEDDED, HOSTED_DELEGATED) the agent's instance id is the
        // truer actor identity than the blueprint client_id, matching the top-level sub idiom
        // used for client-only flows in createJWT.
        if (client.isAgentApplication() && jwt.get(Claims.ACT) == null) {
            Map<String, Object> act = new HashMap<>();
            act.put(Claims.SUB, resolveAgentActSubject(request, client));
            if (client.getAgentType() != null) {
                act.put(Claims.SUB_PROFILE, client.getAgentType().name().toLowerCase());
            }
            jwt.put(Claims.ACT, act);
        }

        // Agent application - advertise client_profile per draft-mora-oauth-entity-profiles-01 §3.3
        // Format: "ai_agent <profile_token>" (registry token + space + lowercase sub-profile).
        if (client.isAgentApplication() && client.getAgentType() != null && jwt.get(CLIENT_PROFILE) == null) {
            jwt.put(CLIENT_PROFILE, ClientProfile.AI_AGENT + " " + client.getAgentType().name().toLowerCase());
        }

        // Agent application - emit sub_profile only when the subject is the agent itself
        // (client-credentials / autonomous flows). For user-bound flows (USER_EMBEDDED auth code,
        // HOSTED_DELEGATED token exchange with a user subject) the subject is the end-user, so
        // top-level sub_profile must not advertise the agent profile — the agent identity is
        // already carried via client_profile and act.sub_profile.
        if (client.isAgentApplication() && client.getAgentType() != null
                && request.isClientOnly() && jwt.get(Claims.SUB_PROFILE) == null) {
            jwt.put(Claims.SUB_PROFILE, client.getAgentType().name().toLowerCase());
        }

        // Apply resource to aud
        setResources(request, jwt);

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

        // Store originally granted resources in refresh token for RFC 8707 compliance
        setOrigResourcesClaim(jwt, request);

        // DPoP (RFC 9449 §3): bind the refresh token to the same key as the access token so the
        // binding travels inside the signed token and can be read back without a store lookup.
        final String dpopJkt = request.getConfirmationMethodJkt();
        if (dpopJkt != null) {
            jwt.setConfirmationMethod(Maps.<String, Object>builder().put(JWT.CONFIRMATION_METHOD_JWK_THUMBPRINT, dpopJkt).build());
        }

        return jwt;
    }

    private JWT createJWT(OAuth2Request oAuth2Request, Client client, User user) {
        JWT jwt = new JWT();
        jwt.setIss(openIDDiscoveryService.getIssuer(oAuth2Request.getOrigin()));
        if (oAuth2Request.isClientOnly()) {
            // For blueprint-agent jwt-bearer assertions in client_credentials flow, use the agent instance ID as sub
            jwt.setSub(resolveAgentSubject(client));
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

        // set authorization_details (RFC 9396 RAR)
        List<Map<String, Object>> authorizationDetails = oAuth2Request.getAuthorizationDetails();
        if (authorizationDetails != null && !authorizationDetails.isEmpty()) {
            jwt.put(AUTHORIZATION_DETAILS, authorizationDetails);
        }

        return jwt;
    }

    private void enhanceJWT(JWT jwt, List<TokenClaim> customClaims, TokenTypeHint tokenTypeHint, ExecutionContext executionContext) {
        if (customClaims != null && !customClaims.isEmpty()) {
            ExecutionContextTokenEnhancer enhancer = new ExecutionContextTokenEnhancer();
            enhancer.enhanceToken(jwt, tokenTypeHint, customClaims, executionContext);
        }
    }

    private Completable storeTokens(JWT accessToken, Map<String, Object> accessTokenWithheldClaims,
                                    JWT refreshToken, Map<String, Object> refreshTokenWithheldClaims,
                                    OAuth2Request oAuth2Request, User user) {
        return tokenRepository.create(
                convert(accessToken, accessTokenWithheldClaims, refreshToken, oAuth2Request, user),
                refreshToken != null ? convert(refreshToken, refreshTokenWithheldClaims, user, oAuth2Request) : null);
    }

    private Single<TokenWithCertificateInfo> enhanceToken(OAuth2Request oAuth2Request, Client client, User endUser, ExecutionContext executionContext, TokenWithCertificateInfo tokenWithCertificateInfo) {
        return tokenEnhancer.enhance(tokenWithCertificateInfo.token, oAuth2Request, client, endUser, executionContext).map(token -> new TokenWithCertificateInfo(token, tokenWithCertificateInfo.certificateInfo));
    }

    // act.sub is the counterpart to the token's top-level sub. In user-bound flows the top-level sub
    // is the end-user, so the agent instance id (when known) is the truer actor identity to expose.
    // In client-only flows (client_credentials, incl. SPIFFE per-instance blueprints) the top-level
    // sub is already the agent instance id, so act.sub must point to the blueprint client_id.
    private static String resolveAgentActSubject(OAuth2Request request, Client client) {
        return request.isClientOnly() ? client.getClientId() : resolveAgentSubject(client);
    }

    // The agent's subject identity: its instance id when known, otherwise the blueprint client_id.
    private static Set<String> withAllowlist(Set<String> reservedClaims, List<String> allowlist) {
        return concat(reservedClaims.stream(), allowlist.stream()).collect(Collectors.toSet());
    }

    private static String resolveAgentSubject(Client client) {
        return client.getAgentInstanceId() != null ? client.getAgentInstanceId() : client.getClientId();
    }

    private void setResources(OAuth2Request request, JWT jwt) {
        Set<String> resource = request.getResources();
        if (resource == null || resource.isEmpty()) {
            return;
        }

        var jsonArray = new JSONArray();
        jsonArray.addAll(resource);

        log.debug("resources: {}, JTI: {}, client ID:{}", jsonArray, jwt.getJti(), request.getClientId());

        jwt.put(Claims.AUD, jsonArray);
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
            log.debug("Refresh token {} stored: {}, JTI: {}", Claims.ORIG_RESOURCES, jsonArray, jwt.getJti());
        } else {
            log.debug("No {} to store in refresh token, JTI: {}", Claims.ORIG_RESOURCES, jwt.getJti());
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
            log.debug("Using original authorization resources from request field: {}", originalAuthorizationResources);
            return new java.util.LinkedHashSet<>(originalAuthorizationResources);
        }

        // 3) Fall back to current request resources
        Set<String> originalResources = request.getResources();
        log.debug("Falling back to request resources: {}", originalResources);
        return originalResources == null ? java.util.Set.of() : new java.util.LinkedHashSet<>(originalResources);
    }

    private record TokenWithCertificateInfo(Token token, CertificateInfo certificateInfo) {}

    /**
     * Convert JWT object to Access Token Response Format
     * @param accessToken access token
     * @param encodedAccessToken access token JWT compact string format
     * @param encodedRefreshToken refresh token JWT compact string format
     * @param oAuth2Request oauth2 token or authorization request
     * @return object containing: Access Token Response Format and Certificate Info
     */
    private TokenWithCertificateInfo convert(JWT accessToken, EncodedJWT encodedAccessToken, EncodedJWT encodedRefreshToken, OAuth2Request oAuth2Request) {
        AccessToken token = new AccessToken(encodedAccessToken.encodedToken());
        token.setSubject(accessToken.getSub());
        token.setExpiresIn(Instant.ofEpochSecond(accessToken.getExp()).minusMillis(System.currentTimeMillis()).getEpochSecond());
        token.setScope(accessToken.getScope());

        if (accessToken.getConfirmationMethod() instanceof Map<?, ?> cnf
                && cnf.containsKey(JWT.CONFIRMATION_METHOD_JWK_THUMBPRINT)) {
            token.setTokenType(DPOP_AUTH_SCHEME);
        }

        // Token Exchange (RFC 8693) specific fields
        if (oAuth2Request.getIssuedTokenType() != null) {
            token.setIssuedTokenType(oAuth2Request.getIssuedTokenType());
        }

        if (accessToken.getExp() > 0) {
            token.setExpireAt(new Date(accessToken.getExp() * 1000L));
        }

        // set additional information
        if (!strictResponse && oAuth2Request.getAdditionalParameters() != null && !oAuth2Request.getAdditionalParameters().isEmpty()) {
            oAuth2Request.getAdditionalParameters().toSingleValueMap().entrySet().stream()
                    .filter(e -> !Token.getStandardParameters().contains(e.getKey()) && !e.getKey().equals(ID_TOKEN))
                    .forEach(e -> token.getAdditionalInformation().put(e.getKey(), e.getValue()));
        }
        // set authorization_details as top-level token-response member (RFC 9396 RAR)
        List<Map<String, Object>> authorizationDetails = oAuth2Request.getAuthorizationDetails();
        if (authorizationDetails != null && !authorizationDetails.isEmpty()) {
            token.getAdditionalInformation().put(AUTHORIZATION_DETAILS, authorizationDetails);
        }
        Set<String> resources = oAuth2Request.getResources();
        if (oAuth2Request.isResourceGranted() && resources != null && resources.size() == 1) {
            token.getAdditionalInformation().put(RESOURCE, resources.iterator().next());
        }
        // set refresh token
        Optional.ofNullable(encodedRefreshToken).map(EncodedJWT::encodedToken).ifPresent(token::setRefreshToken);
        return new TokenWithCertificateInfo(token, encodedAccessToken.certificateInfo());
    }

    private io.gravitee.am.repository.oauth2.model.AccessToken convert(JWT token, Map<String, Object> withheldClaims, JWT refreshToken, OAuth2Request oAuth2Request, User user) {
        io.gravitee.am.repository.oauth2.model.AccessToken accessToken = convertCommon(new io.gravitee.am.repository.oauth2.model.AccessToken(), token, withheldClaims, user, oAuth2Request);
        // set authorization code
        accessToken.setAuthorizationCode(oAuth2Request.parameters() != null ? oAuth2Request.parameters().getFirst(io.gravitee.am.common.oauth2.Parameters.CODE) : null);
        // set refresh token
        accessToken.setRefreshToken(refreshToken != null ? refreshToken.getJti() : null);
        return accessToken;
    }

    private io.gravitee.am.repository.oauth2.model.RefreshToken convert(JWT token, Map<String, Object> withheldClaims, User user, OAuth2Request request) {
        return convertCommon(new io.gravitee.am.repository.oauth2.model.RefreshToken(), token, withheldClaims, user, request);
    }

    private <T extends io.gravitee.am.repository.oauth2.model.Token> T convertCommon(T newToken, JWT sourceToken, Map<String, Object> withheldClaims, User user, OAuth2Request request) {
        newToken.setId(RandomString.generate());
        newToken.setToken(sourceToken.getJti());
        newToken.setDomain(sourceToken.getDomain());
        newToken.setClient(request.getClientId());
        newToken.setAllParentJtis(request.getAllParentJtis());
        // keep reference to userId in the storage, only outside world has to see the sub which maybe based on source+extId
        newToken.setSubject(user == null ? sourceToken.getSub() : user.getId());
        newToken.setCreatedAt(new Date(sourceToken.getIat() * 1000));
        newToken.setExpireAt(new Date(sourceToken.getExp() * 1000));
        newToken.setJkt(request.getConfirmationMethodJkt());
        newToken.setWithheldClaims(withheldClaims);
        return newToken;
    }

    private Map<String, Object> withholdAccessTokenClaims(Client client, JWT accessToken) {
        return withholdClaims(client, accessToken, LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS);
    }

    private Map<String, Object> withholdRefreshTokenClaims(Client client, JWT refreshToken) {
        return withholdClaims(client, refreshToken, LIGHTWEIGHT_REFRESH_TOKEN_CLAIMS);
    }

    private Map<String, Object> withholdClaims(Client client, JWT jwt, Set<String> reservedClaims) {
        Set<String> retainedClaims = withAllowlist(reservedClaims, client.lightweightJwtAllowedAccessTokenClaims());
        Map<String, Object> withheldClaims = new HashMap<>(jwt);
        withheldClaims.keySet().removeAll(retainedClaims);
        jwt.retainClaims(retainedClaims);
        return withheldClaims.isEmpty() ? null : objectMapper.convertValue(withheldClaims, CLAIMS_TYPE);
    }

    private ClientTokenAuditBuilder buildTokenCreatedAudit(OAuth2Request oAuth2Request, Client client, User endUser,
                                                           JWT accessToken, JWT refreshToken,
                                                           TokenWithCertificateInfo tokenWithCertificateInfo) {
        Token enhancedToken = tokenWithCertificateInfo.token();

        return AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .accessToken(accessToken.getJti())
                .refreshToken(refreshToken != null ? refreshToken.getJti() : null)
                .idTokenFor(enhancedToken.getAdditionalInformation().getOrDefault("id_token", null) != null ? endUser : null)
                .tokenActor(client)
                .agentApplication(client)
                .withParams(() -> OAuth2RequestParams.of(oAuth2Request, tokenWithCertificateInfo.certificateInfo()))
                .tokenTarget(endUser)
                .accessTokenSubject(enhancedToken.getSubject());
    }
}
