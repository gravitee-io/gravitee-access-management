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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa;

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAEnrollStep extends MFAStep {

    private final FactorManager factorManager;

    public MFAEnrollStep(Handler<RoutingContext> wrapper, RuleEngine ruleEngine, FactorManager factorManager) {
        super(wrapper, ruleEngine);
        this.factorManager = factorManager;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        var context = new MfaFilterContext(routingContext, client, factorManager);

        final boolean skipMFA = isNull(client) || noFactor(client) || userHasFactor(context) || isMfaSkipped(context);
        if (skipMFA) {
            flow.doNext(routingContext);
        } else {
            flow.exit(this);
        }
    }

    private boolean noFactor(Client client) {
        final Set<String> factors = client.getFactors();
        return isNull(factors) || factors.isEmpty() || onlyRecoveryCodeFactor(factors);
    }

    private boolean onlyRecoveryCodeFactor(Set<String> factors) {
        if (factors.size() == 1) {
            final String factorId = factors.stream().findFirst().get();
            final Factor factor = factorManager.getFactor(factorId);
            return factor.getFactorType().equals(FactorType.RECOVERY_CODE);
        }
        return false;
    }

    private boolean userHasFactor(MfaFilterContext context) {
        return context.hasEndUserAlreadyEnrolled() || context.userHasMatchingFactors();
    }

    public boolean isMfaSkipped(MfaFilterContext context) {
        // We need to check whether the AMFA rule is false since we don't know
        final boolean mfaSkipped = context.isMfaSkipped();
        final String mfaStepUpRule = context.getStepUpRule();
        return isNullOrEmpty(mfaStepUpRule) && mfaSkipped;
    }
}
