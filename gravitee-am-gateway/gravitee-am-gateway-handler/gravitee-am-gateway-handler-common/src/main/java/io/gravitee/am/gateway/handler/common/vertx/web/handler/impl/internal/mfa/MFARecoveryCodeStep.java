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
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFARecoveryCodeStep extends MFAStep {
    private final FactorManager factorManager;

    public MFARecoveryCodeStep(Handler<RoutingContext> handler, RuleEngine ruleEngine, FactorManager factorManager) {
        super(handler, ruleEngine);
        this.factorManager = factorManager;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        final User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();

        if (endUser.getFactors() == null || endUser.getFactors().isEmpty()) {
            flow.doNext(routingContext);
            return;
        }

        if (hasActiveRecoveryCode(endUser) || recoveryFactorDisabled(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY))) {
            flow.doNext(routingContext);
            return;
        }

        flow.exit(this);
    }

    private boolean hasActiveRecoveryCode(User endUser) {
        return hasRecoveryCode(endUser) && isRecoveryCodeActivated(endUser);
    }

    private boolean hasRecoveryCode(User user) {
        if (user.getFactors() == null) {
            return false;
        }

        return user.getFactors()
                .stream()
                .anyMatch(ftr -> ftr.getSecurity() != null && RECOVERY_CODE.equals(ftr.getSecurity().getType()));
    }

    private boolean isRecoveryCodeActivated(User user) {
        if (user.getFactors() == null) {
            return false;
        }

        return user.getFactors()
                .stream()
                .filter(ftr -> ftr.getSecurity() != null && RECOVERY_CODE.equals(ftr.getSecurity().getType()))
                .anyMatch(ftr -> FactorStatus.ACTIVATED.equals(ftr.getStatus()));
    }

    private boolean recoveryFactorDisabled(Client client) {
        Set<String> factors = client.getFactors();
        if(factors == null || factors.isEmpty()){
            return false;
        }

        return client.getFactors()
                .stream()
                .map(factorManager::getFactor)
                .filter(Objects::nonNull)
                .noneMatch(factor -> factor.is(FactorType.RECOVERY_CODE));
    }
}
