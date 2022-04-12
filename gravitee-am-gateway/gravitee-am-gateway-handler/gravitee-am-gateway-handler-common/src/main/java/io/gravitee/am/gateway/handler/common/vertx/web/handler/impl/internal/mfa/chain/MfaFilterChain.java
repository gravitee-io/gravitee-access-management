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

package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.chain;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChain;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFAStep;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Objects.isNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MfaFilterChain {

    private final List<Supplier<Boolean>> suppliers;

    @SafeVarargs
    public MfaFilterChain(Supplier<Boolean>... suppliers) {
        this.suppliers = isNull(suppliers) ? List.of() : List.of(suppliers);
    }

    public void doFilter(MFAStep mfaStep,
                         AuthenticationFlowChain flow,
                         RoutingContext routingContext) {
        doFilter(mfaStep, flow, routingContext, false);
    }

    public void doFilter(MFAStep mfaStep,
                         AuthenticationFlowChain flow,
                         RoutingContext routingContext,
                         boolean setStronglyAuth) {
        boolean skipMFA = suppliers.stream().anyMatch(Supplier::get);
        if (skipMFA) {
            // Since we skip MFA, we need to input in the session that the user has
            // completed MFA already without the need to verify a code
            if (setStronglyAuth) {
                routingContext.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            }
            flow.doNext(routingContext);
        } else {
            flow.exit(mfaStep);
        }
    }

}
