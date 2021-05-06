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
import io.gravitee.am.gateway.handler.context.EvaluableExecutionContext;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class MFAStep extends AuthenticationFlowStep {

    private static final Logger logger = LoggerFactory.getLogger(MFAStep.class);

    public MFAStep(Handler<RoutingContext> handler) {
        super(handler);
    }

    protected boolean isUserStronglyAuth(RoutingContext routingContext) {
        return routingContext.session().get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY) != null &&
                routingContext.session().get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY).equals(true);
    }

    protected boolean isMfaSkipped(RoutingContext routingContext) {
        return routingContext.session().get(ConstantKeys.MFA_SKIPPED_KEY) != null &&
                routingContext.session().get(ConstantKeys.MFA_SKIPPED_KEY).equals(true);
    }

    protected boolean isStepUpAuthentication(RoutingContext routingContext, String selectionRule) {
        try {
            Expression expression = new SpelExpressionParser().parseExpression(selectionRule);

            StandardEvaluationContext evaluation = new StandardEvaluationContext();
            evaluation.setVariable("request", new EvaluableRequest(new VertxHttpServerRequest(routingContext.request().getDelegate())));
            evaluation.setVariable("context", new EvaluableExecutionContext(routingContext.data()));

            return expression.getValue(evaluation, Boolean.class);
        } catch (ParseException | EvaluationException ex) {
            logger.debug("Unable to evaluate the following expression : {}", selectionRule, ex);
            return false;
        }
    }
}
