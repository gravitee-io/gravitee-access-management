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
package io.gravitee.am.gateway.handler.vertx.endpoint;

import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationCodeResponse;
import io.gravitee.am.gateway.handler.oauth2.response.ImplicitResponse;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.request.AuthorizationRequestFactory;
import io.gravitee.am.gateway.handler.vertx.request.TokenRequestFactory;
import io.gravitee.am.gateway.handler.vertx.util.URIBuilder;
import io.gravitee.am.model.Client;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.authentication.UsernamePasswordAuthenticationToken;
import io.gravitee.am.repository.oauth2.model.request.OAuth2Request;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * The authorization endpoint is used to interact with the resource owner and obtain an authorization grant.
 * The authorization server MUST first verify the identity of the resource owner.
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-3.1">3.1. Authorization Endpoint</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizeEndpointHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizeEndpointHandler.class);
    private final AuthorizationRequestFactory authorizationRequestFactory = new AuthorizationRequestFactory();
    private final TokenRequestFactory tokenRequestFactory = new TokenRequestFactory();
    private ClientService clientService;
    private ApprovalService approvalService;
    private AuthorizationCodeService authorizationCodeService;
    private TokenGranter tokenGranter;

    public AuthorizeEndpointHandler(ClientService clientService, ApprovalService approvalService, AuthorizationCodeService authorizationCodeService, TokenGranter tokenGranter) {
        this.clientService = clientService;
        this.approvalService = approvalService;
        this.authorizationCodeService = authorizationCodeService;
        this.tokenGranter = tokenGranter;
    }

    @Override
    public void handle(RoutingContext context) {
        AuthorizationRequest request = authorizationRequestFactory.create(context.request());
        String clientId = request.getClientId();

        // The authorization server authenticates the resource owner and obtains
        // an authorization decision (by asking the resource owner or by establishing approval via other means).
        User authenticatedUser = context.user();
        if (authenticatedUser == null || ! (authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.vertx.auth.user.User)) {
            throw new AccessDeniedException();
        }

        // If the request fails due to a missing, invalid, or mismatching redirection URI, or if the client identifier is missing or invalid,
        // the authorization server SHOULD inform the resource owner of the error and MUST NOT automatically redirect the user-agent to the
        // invalid redirection URI.
        clientService.findByClientId(clientId)
                .switchIfEmpty(Maybe.error(new InvalidRequestException("No client with id : " + clientId)))
                .flatMapSingle(this::verifyClient)
                .flatMap(client -> resolveRedirectUri(client, request))
                .flatMap(authorizationRequest -> approvalService.checkApproval(authorizationRequest, authenticatedUser.principal().getString("username")))
                .flatMap(authorizationRequest -> createAuthorizationResponse(authorizationRequest, authenticatedUser))
                .subscribe(new SingleObserver<AuthorizationRequest>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onSuccess(AuthorizationRequest authorizationRequest) {
                        if (!authorizationRequest.isApproved()) {
                            context.response().putHeader("location", "/oauth/confirm_access").setStatusCode(302).end();
                        }

                        try {
                            if (OAuth2Constants.TOKEN.equals(authorizationRequest.getResponseType())) {
                                context.response().putHeader("location", buildImplicitGrantRedirectUri(authorizationRequest)).setStatusCode(302).end();
                            } else if (OAuth2Constants.CODE.equals(authorizationRequest.getResponseType())) {
                                context.response().putHeader("location", buildAuthorizationCodeRedirectUri(authorizationRequest)).setStatusCode(302).end();
                            } else {
                                // TODO : handle correct error response (https://tools.ietf.org/html/rfc6749#section-4.2.2.1)
                                context.fail(400);
                            }
                        } catch (Exception e) {
                            logger.error("Failed to redirect to client redirect_uri", e);
                            // TODO : handle correct error response (https://tools.ietf.org/html/rfc6749#section-4.2.2.1)
                            context.fail(500);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("Failed to handle authorization request", e);
                        // TODO : handle correct error response (https://tools.ietf.org/html/rfc6749#section-4.2.2.1)
                        context.fail(e);
                    }
                });

    }

    private Single<Client> verifyClient(Client client) {
        // TODO check client scopes
        List<String> authorizedGrantTypes = client.getAuthorizedGrantTypes();
        if (authorizedGrantTypes == null || authorizedGrantTypes.isEmpty()) {
            throw new UnauthorizedClientException("Client should at least have one authorized grand type");
        }
        if (!containsGrantType(authorizedGrantTypes)) {
            throw new UnauthorizedClientException("Client must at least have authorization_code or implicit grant type enable");
        }
        return Single.just(client);
    }

    private boolean containsGrantType(List<String> authorizedGrantTypes) {
        return authorizedGrantTypes.stream()
                .anyMatch(authorizedGrantType -> OAuth2Constants.AUTHORIZATION_CODE.equals(authorizedGrantType)
                        || OAuth2Constants.IMPLICIT.equals(authorizedGrantType));
    }

    private Single<AuthorizationRequest> resolveRedirectUri(Client client, AuthorizationRequest authorizationRequest) {
        // redirect_uri request parameter is OPTIONAL, but the RFC (rfc6749) assumes that
        // the request fails due to a missing, invalid, or mismatching redirection URI
        // If no redirect_uri request parameter is supplied, the client must at least have one registered redirect uri
        String redirectUri = authorizationRequest.getRedirectUri();
        List<String> registeredClientRedirectUris = client.getRedirectUris();
        if (registeredClientRedirectUris != null && !registeredClientRedirectUris.isEmpty()) {
            redirectUri = obtainMatchingRedirect(registeredClientRedirectUris, redirectUri);
            authorizationRequest.setRedirectUri(redirectUri);
            return Single.just(authorizationRequest);
        } else if (redirectUri != null && !redirectUri.isEmpty()) {
            return Single.just(authorizationRequest);
        } else {
            throw new InvalidRequestException("A redirect_uri must be supplied.");
        }
    }

    private String obtainMatchingRedirect(List<String> redirectUris, String requestedRedirect) {
        // no redirect_uri parameter supplied, return the first client registered redirect uri
        if (requestedRedirect == null) {
            return redirectUris.iterator().next();
        }

        for (String redirectUri : redirectUris) {
            if (redirectMatches(requestedRedirect, redirectUri)) {
                return requestedRedirect;
            }
        }
        throw new InvalidRequestException("Invalid redirect_uri : " + requestedRedirect);
    }

    private boolean redirectMatches(String requestedRedirect, String redirectUri) {
        // TODO add some tests !
        try {
            URL req = new URL(requestedRedirect);
            URL reg = new URL(redirectUri);

            int requestedPort = req.getPort() != -1 ? req.getPort() : req.getDefaultPort();
            int registeredPort = reg.getPort() != -1 ? reg.getPort() : reg.getDefaultPort();

            boolean portsMatch = registeredPort == requestedPort;

            if (reg.getProtocol().equals(req.getProtocol()) &&
                    reg.getHost().equals(req.getHost()) &&
                    portsMatch) {
                return req.getPath().startsWith(reg.getPath());
            }
        } catch (MalformedURLException e) {

        }

        return requestedRedirect.equals(redirectUri);
    }

    private Single<AuthorizationRequest> createAuthorizationResponse(AuthorizationRequest authorizationRequest, User authenticatedUser) {
        // request is not approved, the user will be redirect to the approval page
        if (!authorizationRequest.isApproved()) {
            return Single.just(authorizationRequest);
        }

        // handle response type
        switch(authorizationRequest.getResponseType()) {
            case OAuth2Constants.TOKEN :
                return setImplicitResponse(authorizationRequest);
            case OAuth2Constants.CODE :
                return setAuthorizationCodeResponse(authorizationRequest, authenticatedUser);
            default:
                return Single.just(authorizationRequest);
        }

    }

    private Single<AuthorizationRequest> setAuthorizationCodeResponse(AuthorizationRequest authorizationRequest, User authenticatedUser) {
        // prepare response
        OAuth2Request storedRequest = authorizationRequest.createOAuth2Request(authorizationRequest);
        io.gravitee.am.gateway.handler.vertx.auth.user.User user = (io.gravitee.am.gateway.handler.vertx.auth.user.User) authenticatedUser.getDelegate();
        UsernamePasswordAuthenticationToken userAuthentication = new UsernamePasswordAuthenticationToken(user.getUser().getUsername(), user.getUser(), "", Collections.emptySet());
        OAuth2Authentication oAuth2Authentication = new OAuth2Authentication(storedRequest, userAuthentication);

        return authorizationCodeService.create(oAuth2Authentication)
                .map(code -> {
                    AuthorizationCodeResponse response = new AuthorizationCodeResponse();
                    response.setCode(code.getCode());
                    response.setState(authorizationRequest.getState());
                    authorizationRequest.setResponse(response);
                    return authorizationRequest;
                });
    }

    private Single<AuthorizationRequest> setImplicitResponse(AuthorizationRequest authorizationRequest) {
        TokenRequest tokenRequest = tokenRequestFactory.create(authorizationRequest);
        tokenRequest.setGrantType(OAuth2Constants.IMPLICIT);
        return tokenGranter.grant(tokenRequest)
                .map(accessToken -> {
                    ImplicitResponse response = new ImplicitResponse();
                    response.setAccessToken(accessToken);
                    response.setState(authorizationRequest.getState());
                    authorizationRequest.setResponse(response);
                    return authorizationRequest;
                });
    }

    private String buildImplicitGrantRedirectUri(AuthorizationRequest authorizationRequest) throws URISyntaxException {
        ImplicitResponse authorizationResponse = (ImplicitResponse) authorizationRequest.getResponse();
        AccessToken accessToken = authorizationResponse.getAccessToken();
        URIBuilder uriBuilder = URIBuilder.fromURIString(authorizationRequest.getRedirectUri());
        uriBuilder.addFragmentParameter(AccessToken.ACCESS_TOKEN, accessToken.getValue());
        uriBuilder.addFragmentParameter(AccessToken.TOKEN_TYPE, accessToken.getTokenType());
        uriBuilder.addFragmentParameter(AccessToken.EXPIRES_IN, String.valueOf(accessToken.getExpiresIn()));
        if (accessToken.getScope() != null && !accessToken.getScope().isEmpty()) {
            uriBuilder.addFragmentParameter(AccessToken.SCOPE, accessToken.getScope());
        }
        if (authorizationResponse.getState() != null) {
            uriBuilder.addFragmentParameter(OAuth2Constants.STATE, authorizationRequest.getState());
        }
        return uriBuilder.build().toString();
    }

    private String buildAuthorizationCodeRedirectUri(AuthorizationRequest authorizationRequest) throws URISyntaxException {
        AuthorizationCodeResponse authorizationResponse = (AuthorizationCodeResponse) authorizationRequest.getResponse();
        URIBuilder uriBuilder = URIBuilder.fromURIString(authorizationRequest.getRedirectUri());
        uriBuilder.addParameter(OAuth2Constants.CODE, authorizationResponse.getCode());
        if (authorizationResponse.getState() != null) {
            uriBuilder.addParameter(OAuth2Constants.STATE, authorizationRequest.getState());
        }
        return uriBuilder.build().toString();
    }

    public void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }

    public void setApprovalService(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    public void setAuthorizationCodeService(AuthorizationCodeService authorizationCodeService) {
        this.authorizationCodeService = authorizationCodeService;
    }

    public void setTokenGranter(TokenGranter tokenGranter) {
        this.tokenGranter = tokenGranter;
    }
}
