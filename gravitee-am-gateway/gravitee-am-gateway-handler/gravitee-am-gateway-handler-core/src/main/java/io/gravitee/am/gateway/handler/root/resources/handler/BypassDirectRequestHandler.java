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

package io.gravitee.am.gateway.handler.root.resources.handler;


import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.session.SessionManager;
import io.gravitee.am.gateway.handler.common.session.SessionState;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

import static io.gravitee.am.gateway.handler.common.vertx.utils.RedirectHelper.doRedirect;

/**
 * This handler is used to evaluate if an authentication step is coming from a regular flow
 * or has been accessed directly by the user (hit the login page instead of the authorization endpoint)
 *
 * If the step is not coming from regular flow, the user is redirected to the authorization endpoint
 * in order to evaluate the user session and go to the right action (login, mfa challenge, redirect to the app, etc ...)
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class BypassDirectRequestHandler implements Handler<RoutingContext> {

    private final boolean skipHandlerExecution;
    private final SessionManager sessionManager;
    private final Function<SessionState, Boolean> evaluateRouting;

    public BypassDirectRequestHandler(boolean skip, Function<SessionState, Boolean> evaluateRouting) {
        this.skipHandlerExecution = skip;
        this.sessionManager = new SessionManager();
        this.evaluateRouting = evaluateRouting;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        if (needAuthorizationEndpointRouting(routingContext)) {
            routingContext.session().put(ConstantKeys.SESSION_KEY_AUTH_FLOW_STATE, ConstantKeys.SESSION_KEY_AUTH_FLOW_STATE_ONGOING);
            doRedirect(routingContext);
        } else {
            // ongoing SESSION_KEY_AUTH_FLOW_STATE present into the session
            // continue
            routingContext.next();
        }
    }

    /**
     * SESSION_KEY_AUTH_FLOW_STATE is used by the authorization endpoint to flag an ongoing authentication
     * If this flag is missing, a redirect to the authorization endpoint is triggered to route the user to the right step
     *
     * @param routingContext
     * @return true if a redirect to the authorization endpoint is required, false otherwise
     */
    private boolean needAuthorizationEndpointRouting(RoutingContext routingContext) {
        if (skipHandlerExecution) {
            return false;
        }

        SessionState state = sessionManager.getSessionState(routingContext);

        return evaluateRouting.apply(state);
    }
}
