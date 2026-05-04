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
package io.gravitee.am.gateway.handler.aauth.resources.handler;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapRequest;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthBootstrapInteractHandler.AAUTH_BOOTSTRAP_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Redirects the authenticated user from /aauth/bootstrap-interact to /aauth/bootstrap-consent.
 * Follows the same pattern as {@link AAuthConsentRedirectHandler}.
 *
 * @author GraviteeSource Team
 */
@Slf4j
public class AAuthBootstrapConsentRedirectHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext ctx) {
        AAuthBootstrapRequest request = ctx.get(AAUTH_BOOTSTRAP_CONTEXT_KEY);
        if (request == null) {
            // Bootstrap request might be stored in session after login redirect
            request = ctx.session() != null ? ctx.session().get(AAUTH_BOOTSTRAP_CONTEXT_KEY) : null;
        }

        if (request == null) {
            ctx.fail(new InvalidRequestException("No bootstrap request found"));
            return;
        }

        String consentUrl;
        try {
            consentUrl = UriBuilderRequest.resolveProxyRequest(ctx.request(), ctx.get(CONTEXT_PATH))
                    + "/aauth/bootstrap-consent?code=" + request.getInteractionCode();
        } catch (Exception e) {
            consentUrl = "/aauth/bootstrap-consent?code=" + request.getInteractionCode();
        }

        ctx.response()
                .putHeader("Location", consentUrl)
                .setStatusCode(302)
                .end();
    }
}
