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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization;

import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.exception.InteractionRequiredException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oidc.service.flow.Flow;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

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
public class AuthorizationEndpoint extends AbstractAuthorizationEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationEndpoint.class);
    private static final String CLIENT_CONTEXT_KEY = "client";
    private Flow flow;
    private Domain domain;

    public AuthorizationEndpoint(Flow flow, Domain domain) {
        this.domain = domain;
        this.flow = flow;
    }

    @Override
    public void handle(RoutingContext context) {
        AuthorizationRequest request = createAuthorizationRequest(context);

        // The authorization server authenticates the resource owner and obtains
        // an authorization decision (by asking the resource owner or by establishing approval via other means).
        User authenticatedUser = context.user();
        if (authenticatedUser == null || ! (authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User)) {
            throw new AccessDeniedException();
        }

        // get client
        Client client = context.get(CLIENT_CONTEXT_KEY);

        // get resource owner
        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) authenticatedUser.getDelegate()).getUser();

        flow.run(request, client, endUser)
                .subscribe(authorizationResponse -> {
                    try {
                        doRedirect(context.response(), authorizationResponse.buildRedirectUri());
                    } catch (Exception e) {
                        logger.error("Unable to redirect to client redirect_uri", e);
                        context.fail(new ServerErrorException());
                    }
                }, error -> {
                    if (error instanceof AccessDeniedException) {
                        // check prompt value
                        // if prompt=none and the Client does not have pre-configured consent for the requested Claims, throw interaction_required exception
                        // https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
                        // else redirect to consent user approval page
                        String prompt = request.parameters().getFirst(Parameters.PROMPT);
                        if (prompt != null && Arrays.asList(prompt.split("\\s+")).contains("none")) {
                            context.fail(new InteractionRequiredException("Interaction required"));
                        } else {
                            // TODO should we put this data inside repository to handle cluster environment ?
                            context.session().put(OAuth2Constants.AUTHORIZATION_REQUEST, request);
                            String approvalPage = UriBuilderRequest.resolveProxyRequest(context.request(),"/" + domain.getPath() + "/oauth/confirm_access", null);
                            doRedirect(context.response(), approvalPage);
                        }
                    } else {
                        context.fail(error);
                    }
                });

    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }
}
