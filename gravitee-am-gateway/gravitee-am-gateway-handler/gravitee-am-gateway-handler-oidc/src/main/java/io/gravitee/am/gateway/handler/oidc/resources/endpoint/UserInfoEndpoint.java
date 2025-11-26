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
package io.gravitee.am.gateway.handler.oidc.resources.endpoint;

import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.EncodedJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.utils.Tuple;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.oidc.service.request.ClaimsRequest;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.impl.user.UserEnhancer;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.gravitee.am.common.utils.ConstantKeys.ID_TOKEN_EXCLUDED_CLAIMS;
import static java.util.Optional.ofNullable;

/**
 * The Client sends the UserInfo Request using either HTTP GET or HTTP POST.
 * The Access Token obtained from an OpenID Connect Authentication Request MUST be sent as a Bearer Token, per Section 2 of OAuth 2.0 Bearer Token Usage [RFC6750].
 * It is RECOMMENDED that the request use the HTTP GET method and the Access Token be sent using the Authorization header field.
 *
 * See <a href="http://openid.net/specs/openid-connect-core-1_0.html#UserInfo">5.3.1. UserInfo Request</a>
 *
 * The UserInfo Endpoint is an OAuth 2.0 Protected Resource that returns Claims about the authenticated End-User.
 * To obtain the requested Claims about the End-User, the Client makes a request to the UserInfo Endpoint using an Access Token obtained through OpenID Connect Authentication.
 * These Claims are normally represented by a JSON object that contains a collection of name and value pairs for the Claims.
 *
 * See <a href="http://openid.net/specs/openid-connect-core-1_0.html#UserInfo">5.3. UserInfo Endpoint</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserInfoEndpoint implements Handler<RoutingContext> {

    private final UserEnhancer userEnhancer;
    private final JWTService jwtService;
    private final JWEService jweService;
    private final OpenIDDiscoveryService openIDDiscoveryService;
    private final SubjectManager subjectManager;

    private final boolean legacyOpenidScope;

    public UserInfoEndpoint(UserEnhancer userEnhancer,
                            JWTService jwtService,
                            JWEService jweService,
                            OpenIDDiscoveryService openIDDiscoveryService,
                            Environment environment,
                            SubjectManager subjectManager) {
        this.userEnhancer = userEnhancer;
        this.jwtService = jwtService;
        this.jweService = jweService;
        this.openIDDiscoveryService = openIDDiscoveryService;
        this.legacyOpenidScope = environment.getProperty("legacy.openid.openid_scope_full_profile", boolean.class, false);
        this.subjectManager = subjectManager;
    }

    @Override
    public void handle(RoutingContext context) {
        JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        subjectManager.findUserBySub(accessToken)
                .switchIfEmpty(Single.error(() -> new InvalidTokenException("No user found for this token")))
                // enhance user information
                .flatMap(user -> enhance(user, accessToken))
                // process user claims
                .map(user -> Tuple.of(user, processClaims(user, accessToken)))
                // encode response
                .flatMap(tuple -> {
                            final var user = tuple.getT1();
                            final var claims = tuple.getT2();
                            final var jwt = new JWT(claims);
                            subjectManager.updateJWT(jwt, user);
                            if (!expectSignedOrEncryptedUserInfo(client)) {
                                context.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                                return Single.just(Json.encodePrettily(jwt));
                            } else {
                                context.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JWT);

                                jwt.setIss(openIDDiscoveryService.getIssuer(UriBuilderRequest.resolveProxyRequest(context)));
                                jwt.setAud(accessToken.getAud());
                                jwt.setIat(new Date().getTime() / 1000L);
                                jwt.setExp(accessToken.getExp());

                                return jwtService.encodeUserinfo(jwt, client)
                                        .map(EncodedJWT::encodedToken)
                                        //Sign if needed, else return unsigned JWT
                                        .flatMap(userinfo -> jweService.encryptUserinfo(userinfo, client));//Encrypt if needed, else return JWT
                            }
                        }
                )
                .subscribe(
                        buffer -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .end(buffer)
                        ,
                        context::fail
                );
    }

    /**
     * Process user claims against user data and access token information
     * @param user the end user
     * @param accessToken the access token
     * @return user claims
     */
    private Map<String, Object> processClaims(User user, JWT accessToken) {
        final Map<String, Object> additionalInfos = ofNullable(user.getAdditionalInformation()).orElse(Map.of());
        final Map<String, Object> fullProfileClaims = new HashMap<>(additionalInfos);

        // to be sure that this sub value coming from the IDP will not override the one provided by AM
        // we explicitly remove it from the additional info.
        // see https://github.com/gravitee-io/issues/issues/7118
        fullProfileClaims.remove(StandardClaims.SUB);

        Map<String, Object> userClaims = new HashMap<>();
        // prepare requested claims
        Map<String, Object> requestedClaims = new HashMap<>();

        boolean requestForSpecificClaims = false;
        // processing claims list
        // 1. process the request using scope values
        if (accessToken.getScope() != null) {
            final Set<String> scopes = new HashSet<>(Arrays.asList(accessToken.getScope().split("\\s+")));
            requestForSpecificClaims = processScopesRequest(scopes, userClaims, requestedClaims, fullProfileClaims);
        }
        // 2. process the request using the claims values (If present, the listed Claims are being requested to be added to any Claims that are being requested using scope values.
        // If not present, the Claims being requested from the UserInfo Endpoint are only those requested using scope values.)
        if (accessToken.getClaimsRequestParameter() != null) {
            requestForSpecificClaims = processClaimsRequest((String) accessToken.getClaimsRequestParameter(), fullProfileClaims, requestedClaims);
        }

        // remove technical claims that are useless for the calling app
        ID_TOKEN_EXCLUDED_CLAIMS.forEach(userClaims::remove);

        // Exchange the sub claim from the identity provider to its technical id
        final var sub = subjectManager.generateSubFrom(user);
        userClaims.put(StandardClaims.SUB, sub);
        // SUB claim is required
        requestedClaims.put(StandardClaims.SUB, sub);

        return (requestForSpecificClaims) ? requestedClaims : userClaims;
    }

    /**
     * For OpenID Connect, scopes can be used to request that specific sets of information be made available as Claim Values.
     *
     * @param scopes scopes request parameter
     * @param userClaims requested claims from scope
     * @param requestedClaims requested claims
     * @param fullProfileClaims full profile claims
     * @return true if OpenID Connect scopes have been found
     */
    private boolean processScopesRequest(Set<String> scopes,
                                         Map<String, Object> userClaims,
                                         Map<String, Object> requestedClaims,
                                         final Map<String, Object> fullProfileClaims
    ) {
        // if full_profile requested, continue
        // if legacy mode is enabled, also return all if only openid scope is provided
        if (scopes.contains(Scope.FULL_PROFILE.getKey()) ||
                (legacyOpenidScope && scopes.size() == 1 && scopes.contains(Scope.OPENID.getKey()))) {
            userClaims.putAll(fullProfileClaims);
            return false;
        }

        // get requested scopes claims
        final List<String> scopesClaimKeys = scopes.stream()
                .map(String::toUpperCase)
                .filter(scope -> Scope.exists(scope) && !Scope.valueOf(scope).getClaims().isEmpty())
                .map(Scope::valueOf)
                .map(Scope::getClaims)
                .flatMap(List::stream)
                .toList();

        // no OpenID Connect scopes requested continue
        if (scopesClaimKeys.isEmpty()) {
            return false;
        }

        // return specific available sets of information made by scope value request
        scopesClaimKeys.stream()
                .filter(fullProfileClaims::containsKey)
                .forEach(scopeClaim ->
                        requestedClaims.putIfAbsent(scopeClaim, fullProfileClaims.get(scopeClaim))
                );

        return true;
    }

    /**
     * Handle claims request previously made during the authorization request
     * @param claimsValue claims request parameter
     * @param fullProfileClaims user full claims list
     * @param requestedClaims requested claims
     * @return true if userinfo claims have been found
     */
    private boolean processClaimsRequest(String claimsValue, final Map<String, Object> fullProfileClaims, Map<String, Object> requestedClaims) {
        try {
            ClaimsRequest claimsRequest = Json.decodeValue(claimsValue, ClaimsRequest.class);
            if (claimsRequest != null && claimsRequest.getUserInfoClaims() != null) {
                claimsRequest.getUserInfoClaims().forEach((key, value) -> {
                    if (fullProfileClaims.containsKey(key)) {
                        requestedClaims.putIfAbsent(key, fullProfileClaims.get(key));
                    }
                });
                return true;
            }
        } catch (Exception e) {
            // Any members used that are not understood MUST be ignored.
        }
        return false;
    }

    /**
     * Enhance user information with roles and groups if the access token contains those scopes
     * @param user The end user
     * @param accessToken The access token with required scopes
     * @return enhanced user
     */
    private Single<User> enhance(User user, JWT accessToken) {
        if (!loadRoles(accessToken) && !loadGroups(accessToken)) {
            return Single.just(user);
        }

        return userEnhancer.enhance(user)
                .map(user1 -> {
                    Map<String, Object> userClaims = user.getAdditionalInformation() == null ?
                            new HashMap<>() :
                            new HashMap<>(user.getAdditionalInformation());

                    if (user.getRolesPermissions() != null && !user.getRolesPermissions().isEmpty()) {
                        userClaims.putIfAbsent(CustomClaims.ROLES, user.getRolesPermissions().stream().map(Role::getName).collect(Collectors.toList()));
                    }
                    if (user.getGroups() != null && !user.getGroups().isEmpty()) {
                        userClaims.putIfAbsent(CustomClaims.GROUPS, user.getGroups());
                    }
                    user1.setAdditionalInformation(userClaims);
                    return user1;
                });
    }

    /**
     * @param client Client
     * @return Return true if client request signed or encrypted (or both) userinfo.
     */
    private boolean expectSignedOrEncryptedUserInfo(Client client) {
        return client.getUserinfoSignedResponseAlg() != null || client.getUserinfoEncryptedResponseAlg() != null;
    }

    private boolean loadRoles(JWT accessToken) {
        return accessToken.hasScope(Scope.ROLES.getKey());
    }

    private boolean loadGroups(JWT accessToken) {
        return accessToken.hasScope(Scope.GROUPS.getKey());
    }
}
