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

package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.filter;

import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils;
import io.gravitee.am.gateway.handler.context.EvaluableExecutionContext;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.model.oidc.Client;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Session;

import java.util.Map;
import java.util.function.Supplier;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.LOGIN_ATTEMPT_KEY;
import static java.util.Objects.isNull;


/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AdaptiveMfaFilter implements Supplier<Boolean> {

    private final RuleEngine ruleEngine;
    private final Client client;
    private final Map<String, Object> data;
    private final Session session;
    private final HttpServerRequest request;

    public AdaptiveMfaFilter(Client client, Session session, RuleEngine ruleEngine, HttpServerRequest request, Map<String, Object> data) {
        this.client = client;
        this.session = session;
        this.ruleEngine = ruleEngine;
        this.data = data;
        this.request = request;
    }

    @Override
    public Boolean get() {
        String adaptiveAuthenticationRule = MfaUtils.getAdaptiveMfaStepUpRule(client);
        if (isNullOrEmpty(adaptiveAuthenticationRule) || adaptiveAuthenticationRule.isBlank()) {
            return false;
        }
        final Object loginAttempt = session.get(LOGIN_ATTEMPT_KEY);
        data.put(LOGIN_ATTEMPT_KEY, isNull(loginAttempt) ? 0 : loginAttempt);
        var parameters = Map.of(
                "request", new EvaluableRequest(new VertxHttpServerRequest(request.getDelegate())),
                "context", new EvaluableExecutionContext(data)
        );
        return ruleEngine.evaluate(adaptiveAuthenticationRule, parameters, Boolean.class, false);
    }
}
