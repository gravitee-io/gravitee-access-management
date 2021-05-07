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
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.context.EvaluableExecutionContext;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAEnrollStep extends MFAStep {

    public MFAEnrollStep(Handler<RoutingContext> wrapper) {
        super(wrapper);
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
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
        // check if user is already enrolled for MFA
        if (isUserEnrolled(routingContext, endUser, client)) {
            flow.doNext(routingContext);
            return;
        }
        // check if mfa step up rule is set
        String mfaStepUpRule = client.getMfaSettings() != null ? client.getMfaSettings().getStepUpAuthenticationRule() : null;
        if (mfaStepUpRule != null && !mfaStepUpRule.isEmpty()) {
            // if requirements are not met, just continue
            if (!isStepUpAuthentication(routingContext, mfaStepUpRule) &&
                    (isUserStronglyAuth(routingContext) || isMfaSkipped(routingContext))) {
                flow.doNext(routingContext);
                return;
            }
        } else {
            // check if user has skipped enrollment step
            if (isMfaSkipped(routingContext)) {
                flow.doNext(routingContext);
                return;
            }
        }
        // else go to the MFA enroll page
        flow.exit(this);
    }

    private boolean isUserEnrolled(RoutingContext routingContext, io.gravitee.am.model.User user, Client client) {
        if (routingContext.session().get(ConstantKeys.ENROLLED_FACTOR_ID_KEY) != null) {
            return true;
        }

        if (user.getFactors() == null || user.getFactors().isEmpty()) {
            return false;
        }

        return user.getFactors()
                .stream()
                .map(EnrolledFactor::getFactorId)
                .anyMatch(f -> client.getFactors().contains(f));
    }
}
