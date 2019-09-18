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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.approval;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.gateway.handler.oauth2.service.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.net.SocketAddress;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserApprovalProcessHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(UserApprovalProcessHandler.class);
    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String AUTHORIZATION_REQUEST_CONTEXT_KEY = "authorizationRequest";
    private ApprovalService approvalService;
    private Domain domain;

    public UserApprovalProcessHandler(ApprovalService approvalService, Domain domain) {
        this.approvalService = approvalService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        // retrieve client
        Client client = context.get(CLIENT_CONTEXT_KEY);

        // retrieve end user
        User authenticatedUser = context.user();
        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) authenticatedUser.getDelegate()).getUser();

        // retrieve authorization request
        AuthorizationRequest authorizationRequest = context.get(AUTHORIZATION_REQUEST_CONTEXT_KEY);

        // clean prompt values to avoid infinite loop when the authorization flow will be called again
        if (authorizationRequest.getPrompts() != null) {
            authorizationRequest.getPrompts().clear();
            authorizationRequest.parameters().remove(Parameters.PROMPT);
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
        handleApprovalResponse(authorizationRequest, client, endUser, getAuthenticatedUser(context, endUser), h -> {
            if (h.failed()) {
                logger.error("An error occurs while handling authorization approval request", h.cause());
                context.fail(h.cause());
                return;
            }
            context.next();
        });
    }

    private void handleApprovalResponse(AuthorizationRequest authorizationRequest,
                                        Client client,
                                        io.gravitee.am.model.User endUser,
                                        io.gravitee.am.identityprovider.api.User principal,
                                        Handler<AsyncResult<AuthorizationRequest>> handler) {
        approvalService.saveApproval(authorizationRequest, client, endUser, principal)
                .subscribe(
                        authorizationRequest1 -> handler.handle(Future.succeededFuture(authorizationRequest1)),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private io.gravitee.am.identityprovider.api.User getAuthenticatedUser(RoutingContext routingContext, io.gravitee.am.model.User user) {
        io.gravitee.am.identityprovider.api.User authenticatedUser = new DefaultUser(user.getUsername());
        ((DefaultUser) authenticatedUser).setId(user.getId());
        Map<String, Object> additionalInformation = new HashMap<>(user.getAdditionalInformation());
        // add ip address and user agent
        additionalInformation.put(Claims.ip_address, remoteAddress(routingContext.request()));
        additionalInformation.put(Claims.user_agent, userAgent(routingContext.request()));
        additionalInformation.put(Claims.domain, domain.getId());
        ((DefaultUser) authenticatedUser).setAdditionalInformation(additionalInformation);
        return authenticatedUser;
    }

    public String remoteAddress(HttpServerRequest httpServerRequest) {
        String xForwardedFor = httpServerRequest.getHeader(HttpHeaders.X_FORWARDED_FOR);
        String remoteAddress;

        if(xForwardedFor != null && xForwardedFor.length() > 0) {
            int idx = xForwardedFor.indexOf(',');

            remoteAddress = (idx != -1) ? xForwardedFor.substring(0, idx) : xForwardedFor;

            idx = remoteAddress.indexOf(':');

            remoteAddress = (idx != -1) ? remoteAddress.substring(0, idx).trim() : remoteAddress.trim();
        } else {
            SocketAddress address = httpServerRequest.remoteAddress();
            remoteAddress = (address != null) ? address.host() : null;
        }

        return remoteAddress;
    }

    private String userAgent(HttpServerRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
