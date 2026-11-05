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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent;

import io.gravitee.am.common.utils.ConstantKeys;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.function.Predicate;

/**
 * Runs a consent policy flow once per authorization request, whichever consent screens are shown: PRE_CONSENT
 * before the first screen and POST_CONSENT after the last submission.
 */
public class ConsentFlowHandler implements Handler<RoutingContext> {

    private final Handler<RoutingContext> policyChainHandler;
    private final Predicate<RoutingContext> runsHere;

    private ConsentFlowHandler(Handler<RoutingContext> policyChainHandler, Predicate<RoutingContext> runsHere) {
        this.policyChainHandler = policyChainHandler;
        this.runsHere = runsHere;
    }

    /**
     * Skips the detail screen when the scope screen was shown before it.
     */
    public static ConsentFlowHandler preConsent(Handler<RoutingContext> policyChainHandler) {
        return new ConsentFlowHandler(policyChainHandler, context -> ConsentStage.of(context) != ConsentStage.AUTHORIZATION_DETAILS
                || !Boolean.TRUE.equals(context.session().get(ConstantKeys.USER_CONSENT_COMPLETED_KEY)));
    }

    /**
     * Skips the scope submission when the detail screen comes next.
     */
    public static ConsentFlowHandler postConsent(Handler<RoutingContext> policyChainHandler) {
        return new ConsentFlowHandler(policyChainHandler,
                context -> !Boolean.TRUE.equals(context.get(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY)));
    }

    @Override
    public void handle(RoutingContext routingContext) {
        if (runsHere.test(routingContext)) {
            policyChainHandler.handle(routingContext);
        } else {
            routingContext.next();
        }
    }
}
