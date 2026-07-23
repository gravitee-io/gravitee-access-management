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
package io.gravitee.am.gateway.handler.root.resources.handler.common;

import io.gravitee.am.common.exception.oauth2.RedirectMismatchException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.TokenPurpose;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.oauth2.ClientIds;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.root.service.RedirectUriValidator;
import io.gravitee.am.gateway.handler.root.service.RedirectUriValidator.CheckMethod;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.EvaluableRedirectUri;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.getOAuthParameter;
import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.redirectMatches;
import lombok.CustomLog;

/**
 * The authorization server validates the request to ensure that all parameters are valid.
 * If the request is valid, the authorization server authenticates the resource owner and obtains
 * an authorization decision (by asking the resource owner or by establishing approval via other means).
 * <p>
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.1">4.1.1. Authorization Request</a>
 * <p>
 * This specific handler is checking the validity of the redirect_uri
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class RedirectUriValidationHandler implements Handler<RoutingContext> {


    private final Domain domain;
    private final Function<String, Maybe<JWT>> tokenVerifier;

    public RedirectUriValidationHandler(Domain domain) {
        this.domain = domain;
        this.tokenVerifier = t -> Maybe.empty();
    }

    public RedirectUriValidationHandler(Domain domain, UserService userService) {
        this.domain = domain;
        this.tokenVerifier = t -> userService.verifyToken(t).map(UserToken::getToken);
    }

    @Override
    public void handle(RoutingContext context) {
        getOperation(context)
                .doOnSuccess(op -> parseRedirectUriParameter(context, op))
                .subscribe(x -> context.next(), context::fail);
    }

    private Single<TokenPurpose> getOperation(RoutingContext context) {
        return getAndVerifyToken(context)
                .mapOptional(verified -> {
                    var tokenPurpose = (String) verified.get(ConstantKeys.CLAIM_TOKEN_PURPOSE);
                    return Optional.ofNullable(tokenPurpose)
                            .map(TokenPurpose::of);
                })
                .defaultIfEmpty(TokenPurpose.UNSPECIFIED);
    }

    private Maybe<JWT> getAndVerifyToken(RoutingContext context) {
        if (tokenVerifier == null) {
            return Maybe.empty();
        }
        final String token = context.request().getParam(ConstantKeys.TOKEN_PARAM_KEY);
        if (token == null) {
            return Maybe.empty();
        }
        return tokenVerifier.apply(token);
    }

    private List<String> getRegisteredRedirectUris(RoutingContext context){
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        return client.getRedirectUris();
    }

    private List<String> getFilteredRegisteredRedirectUris(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        List<String> redirectUris = client.getRedirectUris();
        if(redirectUris == null || redirectUris.isEmpty()){
            return List.of();
        }
        return redirectUris.stream()
                .map(uri -> new EvaluableRedirectUri(uri).removeELQueryParams())
                .toList();
    }

    private void parseRedirectUriParameter(RoutingContext context, TokenPurpose operation) {
        String requestedRedirectUri = getOAuthParameter(context, io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI);
        String returnUrl = getOAuthParameter(context, ConstantKeys.RETURN_URL_KEY);
        // process the URI validation if the redirect_uri is present or there is no return_url
        // when return_url is present, that mean we are coming from the MFA Challenge policy
        // and redirect_uri is not required. return_url validation is managed by another handler
        if (!StringUtils.hasLength(returnUrl)) {
            List<String> registeredRedirectUris = domain.isRedirectUriExpressionLanguageEnabled() ?
                    getFilteredRegisteredRedirectUris(context) : getRegisteredRedirectUris(context);
            final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final CheckMethod checkMethod = getCheckMethod(client, requestedRedirectUri);
            RedirectUriValidator validator = new RedirectUriValidator(checkMethod);
            validator.validate(registeredRedirectUris, requestedRedirectUri, operation);
        }
    }

    private @NonNull CheckMethod getCheckMethod(Client client, String requestedRedirectUri) {
        // CIMD clients require exact URI matching, except for RFC 8252 §7.3
        // loopback callbacks when the domain allows localhost redirects — there
        // the port is ignored so native clients can pick an ephemeral one.
        if (!ClientIds.isUrlShaped(client.getClientId())) {
            return this::checkMatchingRedirectUri;
        }

        if (isLoopbackUri(requestedRedirectUri) && domain.isRedirectUriLocalhostAllowed()) {
            return this::checkLoopbackRedirectUri;
        }

        return this::checkExactRedirectUri;
    }

    private static boolean isLoopbackUri(String uri) {
        if (uri == null) {
            return false;
        }
        try {
            String host = UriBuilder.fromURIString(uri).build().getHost();
            return host != null && UriBuilder.isLocalhost(host);
        } catch (URISyntaxException e) {
            log.debug("[redirect-uri-validation] uri={} parse error: {}", uri, e.getMessage());
            return false;
        }
    }

    private void checkLoopbackRedirectUri(String requestedRedirect, List<String> registeredClientRedirectUris) {
        if (registeredClientRedirectUris.stream().anyMatch(registered -> loopbackMatches(requestedRedirect, registered))) {
            return;
        }
        throw new RedirectMismatchException(String.format("The redirect_uri [ %s ] MUST match the registered callback URL for this application", requestedRedirect));
    }

    private static boolean loopbackMatches(String requested, String registered) {
        if (!isLoopbackUri(registered)) {
            log.info("[redirect-uri-validation] candidate registered={} skipped (not loopback)", registered);
            return false;
        }
        try {
            URI req = UriBuilder.fromURIString(requested).build();
            URI reg = UriBuilder.fromURIString(registered).build();
            boolean schemeMatch = Objects.equals(req.getScheme(), reg.getScheme());
            boolean hostMatch = Objects.equals(normalizeHost(req.getHost()), normalizeHost(reg.getHost()));
            boolean pathMatch = Objects.equals(req.getPath(), reg.getPath());
            boolean portMatch = reg.getPort() == -1 || normalizePort(req) == normalizePort(reg);
            return schemeMatch && hostMatch && pathMatch && portMatch;
        } catch (URISyntaxException e) {
            log.info("[redirect-uri-validation] candidate registered={} parse error: {}", registered, e.getMessage());
            return false;
        }
    }

    private static String normalizeHost(String host) {
        return host == null ? null : host.toLowerCase();
    }

    private static int normalizePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return switch (uri.getScheme() == null ? "" : uri.getScheme().toLowerCase()) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
    }

    private void checkMatchingRedirectUri(String requestedRedirect, List<String> registeredClientRedirectUris) {
        validateRedirectUri(requestedRedirect, registeredClientRedirectUris,
                this.domain.isRedirectUriStrictMatching() || this.domain.usePlainFapiProfile());
    }

    private void checkExactRedirectUri(String requestedRedirect, List<String> registeredClientRedirectUris) {
        validateRedirectUri(requestedRedirect, registeredClientRedirectUris, true);
    }

    private void validateRedirectUri(String requestedRedirect, List<String> registeredClientRedirectUris, boolean strictMatching) {
        if (registeredClientRedirectUris
                .stream()
                .noneMatch(registeredClientUri -> redirectMatches(requestedRedirect, registeredClientUri, strictMatching))) {
            throw new RedirectMismatchException(String.format("The redirect_uri [ %s ] MUST match the registered callback URL for this application", requestedRedirect));
        }
    }

}
