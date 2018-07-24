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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization;

import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.request.AuthorizationRequestFactory;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.Maybe;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class AuthorizationEndpointHandler extends AbstractAuthorizationEndpointHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationEndpointHandler.class);
    private final AuthorizationRequestFactory authorizationRequestFactory = new AuthorizationRequestFactory();
    private final AuthorizationRequestResolver authorizationRequestResolver = new AuthorizationRequestResolver();
    private ClientService clientService;
    private ApprovalService approvalService;
    private Domain domain;

    public AuthorizationEndpointHandler(AuthorizationCodeService authorizationCodeService,
                                        TokenGranter tokenGranter,
                                        ClientService clientService,
                                        ApprovalService approvalService,
                                        Domain domain) {
        super(authorizationCodeService, tokenGranter);
        this.clientService = clientService;
        this.approvalService = approvalService;
        this.domain = domain;
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

        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.vertx.auth.user.User) authenticatedUser.getDelegate()).getUser();

        // If the request fails due to a missing, invalid, or mismatching redirection URI, or if the client identifier is missing or invalid,
        // the authorization server SHOULD inform the resource owner of the error and MUST NOT automatically redirect the user-agent to the
        // invalid redirection URI.
        clientService.findByClientId(clientId)
                .switchIfEmpty(Maybe.error(new InvalidRequestException("No client with id : " + clientId)))
                .flatMapSingle(client -> authorizationRequestResolver.resolve(request, client)
                        .flatMap(authorizationRequest -> approvalService.checkApproval(authorizationRequest, client, endUser.getUsername()))
                        .flatMap(authorizationRequest -> createAuthorizationResponse(authorizationRequest, client, endUser)))
                .subscribe(authorizationRequest -> {
                    try {
                        if (!authorizationRequest.isApproved()) {
                            // TODO should we put this data inside repository to handle cluster environment ?
                            context.session().put(OAuth2Constants.AUTHORIZATION_REQUEST, authorizationRequest);
                            String approvalPage = UriBuilderRequest.resolveProxyRequest(context.request(),"/" + domain.getPath() + "/oauth/confirm_access", null, false, false);
                            doRedirect(context.response(), approvalPage);
                        } else {
                            doRedirect(context.response(), buildRedirectUri(authorizationRequest));
                        }
                    } catch (Exception e) {
                        logger.error("Unable to redirect to client redirect_uri", e);
                        context.fail(new ServerErrorException());
                    }
                },
                error -> {
                    logger.error("An exception occurs while handling authorization request", error);
                    context.fail(error);
                });

    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

}
