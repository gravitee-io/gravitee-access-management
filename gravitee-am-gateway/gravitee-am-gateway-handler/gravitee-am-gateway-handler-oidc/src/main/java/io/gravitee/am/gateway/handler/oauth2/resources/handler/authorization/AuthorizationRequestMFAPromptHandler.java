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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.oidc.Prompt;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.session.SessionManager;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Initiating MFA Registration via OpenID Connect 1.0
 *
 * An extension to the OpenID Connect Authentication Framework defining a new value for the prompt parameter
 * that instructs the OpenID Provider to start the user factor enrollment experience
 * and after the user factor has been created return the requested tokens to the client to complete the authentication flow.
 *
 * Based on the following spec : https://openid.net/specs/openid-connect-prompt-create-1_0.html but for the MFA process
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestMFAPromptHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationRequestMFAPromptHandler.class);
    private static final String MFA_ENROLL_PATH = "/mfa/enroll";
    private static final String MFA_CHALLENGE_PATH = "/mfa/challenge";
    private final SessionManager sessionManager = new SessionManager();

    @Override
    public void handle(RoutingContext context) {
        final AuthorizationRequest authorizationRequest = context.get(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY);
        // no authorization request, continue
        if (authorizationRequest == null) {
            context.next();
            return;
        }
        final Set<String> prompts = authorizationRequest.getPrompts();
        // no prompt value, continue
        if (prompts == null || !prompts.contains(Prompt.MFA_ENROLL)) {
            context.next();
            return;
        }
        final var mfaState = sessionManager.getSessionState(context).getMfaState();

        // if MFA enrollment not completed, redirect to MFA Enroll page
        if (!Boolean.TRUE.equals(context.session().get(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY)) || mfaState.isEnrollmentCompleted()) {
            redirect(context, MFA_ENROLL_PATH);
            return;
        }
        // if MFA challenge not completed, redirect to MFA Challenge page
        if (!Boolean.TRUE.equals(context.session().get(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY)) || mfaState.isChallengeCompleted()) {
            redirect(context, MFA_CHALLENGE_PATH);
            return;
        }
        context.next();
    }

    private void redirect(RoutingContext context, String path) {
        final HttpServerRequest request = context.request();
        final String mfaPage = context.get(CONTEXT_PATH) + path;
        try {
            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(request);
            String proxiedRedirectURI = UriBuilderRequest.resolveProxyRequest(request, mfaPage, queryParams, true);
            request.response()
                    .putHeader(HttpHeaders.LOCATION, proxiedRedirectURI)
                    .setStatusCode(302)
                    .end();
        } catch (Exception e) {
            logger.warn("Failed to decode MFA redirect url", e);
            request.response()
                    .putHeader(HttpHeaders.LOCATION, mfaPage)
                    .setStatusCode(302)
                    .end();
        }
    }
}
