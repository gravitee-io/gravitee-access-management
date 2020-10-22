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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal;

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnRegisterStep extends AuthenticationFlowStep {

    private final Domain domain;

    public WebAuthnRegisterStep(Domain domain, Handler<RoutingContext> wrapper) {
        super(wrapper);
        this.domain = domain;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final Session session = routingContext.session();
        final io.gravitee.am.model.User endUser = ((User) routingContext.user().getDelegate()).getUser();

        // check if WebAuthn is enabled
        LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        if (loginSettings == null || !loginSettings.isPasswordlessEnabled()) {
            flow.doNext(routingContext);
            return;
        }
        // check if user is already authenticated with passwordless
        if (Boolean.TRUE.equals(session.get(ConstantKeys.PASSWORDLESS_AUTH_COMPLETED_KEY))) {
            flow.doNext(routingContext);
            return;
        }
        // check if user has skipped registration step
        if (Boolean.TRUE.equals(session.get(ConstantKeys.WEBAUTHN_SKIPPED_KEY))) {
            flow.doNext(routingContext);
            return;
        }
        // check if user is already registered for WebAuthn
        if (isUserEnrolled(endUser)) {
            flow.doNext(routingContext);
            return;
        }
        // else go to the WebAuthn registration page
        flow.exit(this);
    }

    private boolean isUserEnrolled(io.gravitee.am.model.User user) {
        return user.isWebAuthnRegistrationCompleted();
    }
}
