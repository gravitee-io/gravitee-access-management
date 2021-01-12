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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSession;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.AUTH_FLOW_CONTEXT_VERSION_KEY;
import static java.util.Optional.ofNullable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationFlowContextHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFlowContextHandler.class);

    private AuthenticationFlowContextService authenticationFlowContextService;

    private final boolean exitOnError;

    public AuthenticationFlowContextHandler(AuthenticationFlowContextService authenticationFlowContextService, Environment env) {
        this.authenticationFlowContextService = authenticationFlowContextService;
        this.exitOnError = env.getProperty("authenticationFlow.exitOnError", Boolean.class, Boolean.FALSE);
    }

    @Override
    public void handle(RoutingContext context) {
        CookieSession session = (CookieSession) context.session().getDelegate();
        if (session != null && !session.isDestroyed()) {
            final String transactionId = session.get(ConstantKeys.TRANSACTION_ID_KEY);
            final int version = ofNullable((Number) session.get(AUTH_FLOW_CONTEXT_VERSION_KEY)).map(Number::intValue).orElse(1);
            authenticationFlowContextService.loadContext(transactionId, version)
                    .subscribe(
                            ctx ->  {
                                // store the AuthenticationFlowContext in order to provide all related information about this context
                                context.put(ConstantKeys.AUTH_FLOW_CONTEXT_KEY, ctx);
                                // store only the AuthenticationFlowContext.data attributes in order to simplify EL templating
                                // and provide an up to date set of data if the enrichAuthFlow Policy ius used multiple time in a step
                                // {#context.attributes['authFlow']['entry']}
                                context.put(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY, ctx.getData());
                                context.next();
                            },
                            error -> {
                                LOGGER.warn("AuthenticationFlowContext can't be loaded", error);
                                if (exitOnError) {
                                    context.fail(error);
                                } else {
                                    context.next();
                                }
                            });
        }
    }
}
