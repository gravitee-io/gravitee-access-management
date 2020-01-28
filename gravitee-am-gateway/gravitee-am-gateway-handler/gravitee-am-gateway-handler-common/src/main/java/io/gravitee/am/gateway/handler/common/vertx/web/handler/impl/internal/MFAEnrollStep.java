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

import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAEnrollStep extends AuthenticationFlowStep {
    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String MFA_SKIPPED_KEY = "mfaEnrollmentSkipped";
    private static final String STRONG_AUTH_COMPLETED  = "strongAuthCompleted";
    private static final String ENROLLED_FACTOR_KEY = "enrolledFactor";

    public MFAEnrollStep(Handler<RoutingContext> wrapper) {
        super(wrapper);
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        final Session session = routingContext.session();
        final io.gravitee.am.model.User endUser = ((User) routingContext.user().getDelegate()).getUser();

        // check if application has enabled MFA
        if (client == null) {
            flow.doNext(routingContext);
            return;
        }
        if (client.getFactors() == null || client.getFactors().isEmpty()) {
            flow.doNext(routingContext);
            return;
        }
        // check if user is already authenticated with strong auth
        if (session.get(STRONG_AUTH_COMPLETED) != null && session.get(STRONG_AUTH_COMPLETED).equals(true)) {
            flow.doNext(routingContext);
            return;
        }
        // check if user has skipped enrollment step
        if (session.get(MFA_SKIPPED_KEY) != null && session.get(MFA_SKIPPED_KEY).equals(true)) {
            flow.doNext(routingContext);
            return;
        }
        // check if user is already enrolled for MFA
        if (isUserEnrolled(routingContext, endUser, client)) {
            flow.doNext(routingContext);
            return;
        }
        // else go to the MFA enroll page
        flow.exit(this);
    }

    private boolean isUserEnrolled(RoutingContext routingContext, io.gravitee.am.model.User user, Client client) {
        if (routingContext.session().get(ENROLLED_FACTOR_KEY) != null) {
            return true;
        }

        if (user.getFactors() == null || user.getFactors().isEmpty()) {
            return false;
        }

        return user.getFactors()
                .stream()
                .map(enrolledFactor -> enrolledFactor.getFactorId())
                .anyMatch(f -> client.getFactors().contains(f));
    }
}
