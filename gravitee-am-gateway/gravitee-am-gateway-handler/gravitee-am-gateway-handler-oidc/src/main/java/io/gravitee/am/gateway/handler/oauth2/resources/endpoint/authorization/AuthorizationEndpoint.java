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

import io.gravitee.am.common.oauth2.ResponseMode;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.service.par.PushedAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.response.AuthorizationResponse;
import io.gravitee.am.gateway.handler.oidc.service.flow.Flow;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.auth.User;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.ACTION_KEY;

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
public class AuthorizationEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationEndpoint.class);
    private static final String FORM_PARAMETERS = "parameters";
    private final Flow flow;
    private final ThymeleafTemplateEngine engine;
    private final PushedAuthorizationRequestService parService;

    public AuthorizationEndpoint(Flow flow, ThymeleafTemplateEngine engine, PushedAuthorizationRequestService parService) {
        this.flow = flow;
        this.engine = engine;
        this.parService = parService;
    }

    @Override
    public void handle(RoutingContext context) {
        // The authorization server authenticates the resource owner and obtains
        // an authorization decision (by asking the resource owner or by establishing approval via other means).
        User authenticatedUser = context.user();
        if (authenticatedUser == null || !(authenticatedUser.getDelegate() instanceof io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User)) {
            throw new AccessDeniedException();
        }

        // get authorization request
        AuthorizationRequest request = context.get(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY);

        // get client
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        // get resource owner
        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) authenticatedUser.getDelegate()).getUser();

        final String uriIdentifier = context.get(ConstantKeys.REQUEST_URI_ID_KEY);
        parService.deleteRequestUri(uriIdentifier).onErrorResumeNext((err) -> {
                    logger.warn("Deletion of Pushed Authorization Request with id '{}' failed", uriIdentifier, err);
                    return Completable.complete();
                })
                .andThen(flow.run(request, client, endUser))
                .subscribe(
                        authorizationResponse -> {
                            try {
                                // final step of the authorization flow, we can clean the session and redirect the user
                                cleanSession(context);
                                doRedirect(context, request, authorizationResponse);
                            } catch (Exception e) {
                                logger.error("Unable to redirect to client redirect_uri", e);
                                context.fail(new ServerErrorException());
                            }
                        }, context::fail);

    }

    private void doRedirect(RoutingContext context, AuthorizationRequest request, AuthorizationResponse response) {
        try {
            // if response mode is not set to form_post, the user is redirected to the client callback endpoint
            if (!ResponseMode.FORM_POST.equals(request.getResponseMode())) {
                final String redirectUri = response.buildRedirectUri();
                context
                        .response()
                        .putHeader(HttpHeaders.LOCATION, redirectUri)
                        .setStatusCode(302)
                        .end();
                return;
            }

            // In form_post mode, Authorization Response parameters are encoded as HTML form values that are auto-submitted in the User Agent,
            // and thus are transmitted via the HTTP POST method to the Client.
            // Prepare context to render post form.
            final MultiMap queryParams = RequestUtils.cleanParams(response.params(false));
            context.put(ACTION_KEY, request.getRedirectUri());
            context.put(FORM_PARAMETERS, queryParams.remove(ACTION_KEY));

            // Render Authorization form_post form.
            engine.render(context.data(), "login_sso_post")
                    .subscribe(
                            buffer -> {
                                context.response()
                                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
                                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                                        .end(buffer);
                            },
                            throwable -> {
                                logger.error("Unable to render Authorization form_post page", throwable);
                                context.fail(throwable.getCause());
                            }
                    );
        } catch (Exception e) {
            logger.error("Unable to redirect to client redirect_uri", e);
            context.fail(new ServerErrorException());
        }
    }

    private void cleanSession(RoutingContext context) {
        context.session().remove(ConstantKeys.TRANSACTION_ID_KEY);
        context.session().remove(ConstantKeys.AUTH_FLOW_CONTEXT_VERSION_KEY);
        context.session().remove(ConstantKeys.USER_CONSENT_COMPLETED_KEY);
        context.session().remove(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY);
        context.session().remove(ConstantKeys.WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY);
        context.session().remove(ConstantKeys.PASSWORDLESS_AUTH_ACTION_KEY);
        context.session().remove(ConstantKeys.MFA_FACTOR_ID_CONTEXT_KEY);
        context.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY);
        context.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY);
        context.session().remove(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY);
        context.session().remove(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY);
        context.session().remove(ConstantKeys.USER_LOGIN_COMPLETED_KEY);
        context.session().remove(ConstantKeys.MFA_ENROLL_CONDITIONAL_SKIPPED_KEY);
    }
}
