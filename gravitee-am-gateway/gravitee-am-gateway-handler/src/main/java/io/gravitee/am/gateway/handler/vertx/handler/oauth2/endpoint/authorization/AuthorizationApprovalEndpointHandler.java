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
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.auth.handler.RedirectAuthHandler;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * If the request is valid, the authorization server authenticates the resource owner and obtains
 * an authorization decision (by asking the resource owner or by establishing approval via other means).
 * When a decision is established, the authorization server directs the user-agent to the provided client redirection URI using an HTTP
 * redirection response, or by other means available to it via the user-agent.
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-3.1">3.1. Authorization Endpoint</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationApprovalEndpointHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationApprovalEndpointHandler.class);
    private ApprovalService approvalService;

    public AuthorizationApprovalEndpointHandler(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public void handle(RoutingContext context) {
        // The authorization server authenticates the resource owner and obtains
        // an authorization decision (by asking the resource owner or by establishing approval via other means).
        User authenticatedUser = context.user();
        if (authenticatedUser == null || ! (authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.vertx.auth.user.User)) {
            throw new AccessDeniedException();
        }

        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.vertx.auth.user.User) authenticatedUser.getDelegate()).getUser();

        AuthorizationRequest authorizationRequest = context.session().get(OAuth2Constants.AUTHORIZATION_REQUEST);
        if (authorizationRequest == null) {
            context.response().setStatusCode(400).end("An authorization request is required to handle user approval");
            return;
        }

        HttpServerRequest req = context.request();
        if (!req.isExpectMultipart()) {
            throw new IllegalStateException("Form body not parsed - do you forget to include a BodyHandler?");
        }

        // prepare user approval choices
        MultiMap params = req.formAttributes();

        // retrieve user approval choices
        Map<String, String> approvalParameters = params.getDelegate().entries()
                .stream()
                .filter(entry -> entry.getKey().startsWith(OAuth2Constants.SCOPE_PREFIX))
                .collect(Collectors.toMap(scopeEntry -> scopeEntry.getKey(), scopeEntry -> params.get(OAuth2Constants.USER_OAUTH_APPROVAL)));
        authorizationRequest.setApprovalParameters(approvalParameters);

        // handle approval response
        approvalService.saveApproval(authorizationRequest, endUser.getUsername())
                .subscribe(authorizationRequest1 -> {
                    // user denied access
                    if (!authorizationRequest.isApproved()) {
                        context.fail(new AccessDeniedException("User denied access"));
                        return;
                    }

                    // user approved access, replay authorization request
                    Session session = context.session();
                    if (session != null && session.get(RedirectAuthHandler.DEFAULT_RETURN_URL_PARAM) != null) {
                        final String redirectUrl = session.get(RedirectAuthHandler.DEFAULT_RETURN_URL_PARAM);
                        doRedirect(context.response(), redirectUrl);
                    } else {
                        context.fail(503);
                    }
                },
                error -> {
                    logger.error("An error occurs while handling authorization approval request", error);
                    context.fail(error);
                });
        }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }
}
