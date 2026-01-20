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
package io.gravitee.am.gateway.handler.oauth2.service.grant.impl;

import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.exception.uma.RequiredClaims;
import io.gravitee.am.common.exception.uma.UmaException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.policy.DefaultRule;
import io.gravitee.am.gateway.handler.common.policy.Rule;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.service.uma.UMAPermissionTicketService;
import io.gravitee.am.gateway.handler.common.service.uma.UMAResourceGatewayService;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.model.uma.PermissionTicket;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.context.SimpleExecutionContext;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.common.oauth2.Parameters.*;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.ACCESS_TOKEN;

/**
 * Strategy for UMA 2.0 (User Managed Access) Grant.
 * Handles permission-based access to protected resources.
 *
 * @see <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html">UMA 2.0 Grant</a>
 * @author GraviteeSource Team
 */
public class UmaStrategy implements GrantStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(UmaStrategy.class);
    private static final List<String> CLAIM_TOKEN_FORMAT_SUPPORTED = List.of(TokenType.ID_TOKEN);

    private final UserAuthenticationManager userAuthenticationManager;
    private final UMAPermissionTicketService permissionTicketService;
    private final UMAResourceGatewayService resourceService;
    private final JWTService jwtService;
    private final SubjectManager subjectManager;
    private final RulesEngine rulesEngine;
    private final ExecutionContextFactory executionContextFactory;

    public UmaStrategy(
            UserAuthenticationManager userAuthenticationManager,
            UMAPermissionTicketService permissionTicketService,
            UMAResourceGatewayService resourceService,
            JWTService jwtService,
            SubjectManager subjectManager,
            RulesEngine rulesEngine,
            ExecutionContextFactory executionContextFactory) {
        this.userAuthenticationManager = userAuthenticationManager;
        this.permissionTicketService = permissionTicketService;
        this.resourceService = resourceService;
        this.jwtService = jwtService;
        this.subjectManager = subjectManager;
        this.rulesEngine = rulesEngine;
        this.executionContextFactory = executionContextFactory;
    }

    @Override
    public boolean supports(String grantType, Client client, Domain domain) {
        if (!GrantType.UMA.equals(grantType)) {
            return false;
        }

        // Check if UMA is enabled for domain
        if (domain.getUma() == null || !domain.getUma().isEnabled()) {
            LOGGER.debug("UMA is not enabled for domain: {}", domain.getId());
            return false;
        }

        if (!client.hasGrantType(GrantType.UMA)) {
            LOGGER.debug("Client {} does not support UMA grant type", client.getClientId());
            return false;
        }

        return true;
    }

    @Override
    public Single<TokenCreationRequest> process(TokenRequest request, Client client, Domain domain) {
        LOGGER.debug("Processing UMA grant request for client: {}", client.getClientId());

        return parseRequest(request)
                .flatMap(parsedRequest -> resolveResourceOwner(request, parsedRequest, client))
                .flatMap(context -> resolvePermissions(request, context, client))
                .map(context -> {
                    request.setPermissions(context.permissions);
                    return context;
                })
                .flatMap(context -> executeAccessPolicies(context, request, client))
                .map(context -> createTokenCreationRequest(request, context, client));
    }

    private Single<UmaContext> parseRequest(TokenRequest request) {
        MultiValueMap<String, String> parameters = request.parameters();
        String ticket = parameters.getFirst(TICKET);
        String claimToken = parameters.getFirst(CLAIM_TOKEN);
        String claimTokenFormat = parameters.getFirst(CLAIM_TOKEN_FORMAT);
        String requestingPartyToken = parameters.getFirst(RPT);

        if (ticket == null) {
            return Single.error(new InvalidGrantException("Missing parameter: ticket"));
        }

        // Validate claim_token/claim_token_format pair
        if (claimToken != null ^ claimTokenFormat != null) {
            return Single.error(UmaException.needInfoBuilder(ticket)
                    .requiredClaims(Arrays.asList(
                            RequiredClaims.builder()
                                    .name(CLAIM_TOKEN)
                                    .friendlyName("Requesting party token")
                                    .build(),
                            RequiredClaims.builder()
                                    .name(CLAIM_TOKEN_FORMAT)
                                    .friendlyName("supported claims token format")
                                    .claimTokenFormat(CLAIM_TOKEN_FORMAT_SUPPORTED)
                                    .build()
                    ))
                    .build());
        }

        if (StringUtils.hasText(claimTokenFormat) && !CLAIM_TOKEN_FORMAT_SUPPORTED.contains(claimTokenFormat)) {
            return Single.error(UmaException.needInfoBuilder(ticket)
                    .requiredClaims(List.of(RequiredClaims.builder()
                            .name(CLAIM_TOKEN_FORMAT)
                            .friendlyName("supported claims token format")
                            .claimTokenFormat(CLAIM_TOKEN_FORMAT_SUPPORTED)
                            .build()))
                    .build());
        }

        return Single.just(new UmaContext(ticket, claimToken, requestingPartyToken, null, null));
    }

    private Single<UmaContext> resolveResourceOwner(TokenRequest request, UmaContext context, Client client) {
        if (!StringUtils.hasText(context.claimToken)) {
            return Single.just(context);
        }

        return jwtService.decodeAndVerify(context.claimToken, client, ACCESS_TOKEN)
                .flatMapMaybe(jwt -> userAuthenticationManager.loadPreAuthenticatedUserBySub(jwt, request))
                .switchIfEmpty(Maybe.error(UserInvalidException::new))
                .map(user -> context.withUser(user))
                .toSingle()
                .onErrorResumeNext(ex -> Single.error(UmaException.needInfoBuilder(context.ticket)
                        .requiredClaims(List.of(RequiredClaims.builder()
                                .name(CLAIM_TOKEN)
                                .friendlyName("Malformed or expired claim_token")
                                .build()))
                        .build()));
    }

    private Single<UmaContext> resolvePermissions(TokenRequest request, UmaContext context, Client client) {
        // Validate requested scopes against client
        if (request.getScopes() != null && !request.getScopes().isEmpty()) {
            if (client.getScopeSettings() == null || client.getScopeSettings().isEmpty() ||
                    !new HashSet<>(client.getScopeSettings().stream()
                            .map(ApplicationScopeSettings::getScope).toList())
                            .containsAll(request.getScopes())) {
                return Single.error(new InvalidScopeException(
                        "At least one of the scopes included in the request does not match client pre-registered scopes"));
            }
        }

        return permissionTicketService.remove(context.ticket)
                .map(PermissionTicket::getPermissionRequest)
                .flatMap(permissionRequests -> {
                    List<String> resourceIds = permissionRequests.stream()
                            .map(PermissionRequest::getResourceId)
                            .collect(Collectors.toList());

                    return resourceService.findByResources(resourceIds)
                            .collect(Collectors.toList())
                            .flatMap(resources -> checkAndResolveScopes(request, permissionRequests, resources))
                            .flatMap(resolved -> extendWithRpt(request, context, client, resolved))
                            .map(finalPermissions -> context.withPermissions(finalPermissions));
                });
    }

    private Single<List<PermissionRequest>> checkAndResolveScopes(
            TokenRequest request,
            List<PermissionRequest> permissions,
            List<Resource> resources) {

        // Validate requested scopes match resource scopes
        if (request.getScopes() != null && !request.getScopes().isEmpty()) {
            Set<String> allResourceScopes = resources.stream()
                    .map(Resource::getResourceScopes)
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());

            if (!allResourceScopes.containsAll(request.getScopes())) {
                return Single.error(new InvalidScopeException(
                        "At least one of the scopes included in the request does not match resource registered scopes"));
            }
        }

        // Build resource map
        Map<String, Resource> resourceMap = resources.stream()
                .collect(Collectors.toMap(Resource::getId, r -> r));

        // Validate all requested resources still exist (may have been deleted after ticket was issued)
        List<String> missingResources = permissions.stream()
                .map(PermissionRequest::getResourceId)
                .filter(resourceId -> !resourceMap.containsKey(resourceId))
                .collect(Collectors.toList());

        if (!missingResources.isEmpty()) {
            LOGGER.debug("Permission ticket references deleted resources: {}", missingResources);
            return Single.error(new InvalidGrantException(
                    "Permission ticket references resources that no longer exist"));
        }

        // Merge requested scopes into permission requests
        if (request.getScopes() == null || request.getScopes().isEmpty()) {
            return Single.just(permissions);
        }

        List<PermissionRequest> resolved = permissions.stream()
                .map(pr -> {
                    Set<String> registeredScopes = new HashSet<>(
                            resourceMap.get(pr.getResourceId()).getResourceScopes());
                    pr.getResourceScopes().addAll(
                            request.getScopes().stream()
                                    .filter(registeredScopes::contains)
                                    .collect(Collectors.toSet()));
                    return pr;
                })
                .collect(Collectors.toList());

        return Single.just(resolved);
    }

    private Single<List<PermissionRequest>> extendWithRpt(
            TokenRequest request,
            UmaContext context,
            Client client,
            List<PermissionRequest> permissions) {

        if (!StringUtils.hasText(context.requestingPartyToken)) {
            return Single.just(permissions);
        }

        return jwtService.decodeAndVerify(context.requestingPartyToken, client, ACCESS_TOKEN)
                .flatMap(rpt -> validateRpt(rpt, client, context.user))
                .map(rpt -> mergePermissions(rpt, permissions))
                .onErrorResumeNext(ex -> Single.error(
                        new InvalidGrantException("Requesting Party Token (rpt) not valid")));
    }

    private Single<JWT> validateRpt(JWT rpt, Client client, User user) {
        String expectedSub = user != null ? subjectManager.generateSubFrom(user.getFullId()) : client.getClientId();
        if (!expectedSub.equals(rpt.getSub()) || !client.getClientId().equals(rpt.getAud())) {
            return Single.error(InvalidTokenException::new);
        }
        return Single.just(rpt);
    }

    @SuppressWarnings("unchecked")
    private List<PermissionRequest> mergePermissions(JWT rpt, List<PermissionRequest> requested) {
        if (rpt.get("permissions") == null) {
            return requested;
        }

        Map<String, PermissionRequest> newPermissions = requested.stream()
                .collect(Collectors.toMap(PermissionRequest::getResourceId, pr -> pr));

        Map<String, PermissionRequest> rptPermissions = convertPermissions(
                (List<Map<String, Object>>) rpt.get("permissions"));

        return new ArrayList<>(Stream.concat(
                        newPermissions.entrySet().stream(),
                        rptPermissions.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (req, fromRpt) -> {
                            req.setResourceScopes(Stream.concat(
                                            req.getResourceScopes().stream(),
                                            fromRpt.getResourceScopes().stream())
                                    .distinct()
                                    .toList());
                            return req;
                        }))
                .values());
    }

    private Map<String, PermissionRequest> convertPermissions(List<Map<String, Object>> permissions) {
        Map<String, PermissionRequest> result = new LinkedHashMap<>(permissions.size());
        permissions.stream()
                .map(JsonObject::new)
                .map(json -> json.mapTo(PermissionRequest.class))
                .forEach(pr -> result.put(pr.getResourceId(), pr));
        return result;
    }

    /**
     * Execute UMA resource-level access policies.
     * The resource owner configures policy conditions on the authorization server,
     * which are evaluated here before issuing the token.
     *
     * @see <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html">UMA 2.0 Grant</a>
     */
    private Single<UmaContext> executeAccessPolicies(UmaContext context, TokenRequest request, Client client) {
        List<PermissionRequest> permissionRequests = context.permissions;
        if (permissionRequests == null || permissionRequests.isEmpty()) {
            return Single.just(context);
        }

        List<String> resourceIds = permissionRequests.stream()
                .map(PermissionRequest::getResourceId)
                .collect(Collectors.toList());

        return resourceService.findAccessPoliciesByResources(resourceIds)
                .map(accessPolicy -> {
                    Rule rule = new DefaultRule(accessPolicy);
                    permissionRequests.stream()
                            .filter(pr -> pr.getResourceId().equals(accessPolicy.getResource()))
                            .findFirst()
                            .ifPresent(pr -> ((DefaultRule) rule).setMetadata(
                                    Collections.singletonMap("permissionRequest", pr)));
                    return rule;
                })
                .toList()
                .flatMap(rules -> {
                    if (rules.isEmpty()) {
                        return Single.just(context);
                    }
                    ExecutionContext simpleExecutionContext = new SimpleExecutionContext(request, request.getHttpResponse());
                    ExecutionContext executionContext = executionContextFactory.create(simpleExecutionContext);
                    executionContext.setAttribute(ConstantKeys.CLIENT_CONTEXT_KEY, new ClientProperties(client));
                    if (context.user != null) {
                        executionContext.setAttribute(ConstantKeys.USER_CONTEXT_KEY, new UserProperties(context.user, true));
                    }
                    return rulesEngine.fire(rules, executionContext)
                            .toSingleDefault(context)
                            .onErrorResumeNext(ex -> Single.error(
                                    new InvalidGrantException("Policy conditions are not met for actual request parameters")));
                });
    }

    private TokenCreationRequest createTokenCreationRequest(
            TokenRequest request,
            UmaContext context,
            Client client) {

        // UMA doesn't use regular scopes - they're in permissions
        request.setScopes(null);

        // Support refresh token only if user is present
        boolean supportRefresh = context.user != null &&
                client.hasGrantType(GrantType.REFRESH_TOKEN);

        boolean upgraded = context.requestingPartyToken != null;

        return TokenCreationRequest.forUma(
                request,
                context.user,
                context.ticket,
                context.permissions,
                upgraded,
                supportRefresh
        );
    }

    /**
     * Internal context for UMA processing.
     */
    private record UmaContext(
            String ticket,
            String claimToken,
            String requestingPartyToken,
            User user,
            List<PermissionRequest> permissions
    ) {
        UmaContext withUser(User user) {
            return new UmaContext(ticket, claimToken, requestingPartyToken, user, permissions);
        }

        UmaContext withPermissions(List<PermissionRequest> permissions) {
            return new UmaContext(ticket, claimToken, requestingPartyToken, user, permissions);
        }
    }
}
