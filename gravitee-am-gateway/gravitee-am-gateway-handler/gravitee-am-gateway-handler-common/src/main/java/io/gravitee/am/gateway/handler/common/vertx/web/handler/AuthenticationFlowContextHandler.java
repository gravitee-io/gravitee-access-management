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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSession;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.common.utils.UUID;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import static io.gravitee.am.common.utils.ConstantKeys.AUTH_FLOW_CONTEXT_VERSION_KEY;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.Optional.ofNullable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationFlowContextHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFlowContextHandler.class);

    private final AuthenticationFlowContextService authenticationFlowContextService;

    private final boolean exitOnError;

    public AuthenticationFlowContextHandler(AuthenticationFlowContextService authenticationFlowContextService,
                                            Environment env) {
        this.authenticationFlowContextService = authenticationFlowContextService;
        this.exitOnError = env.getProperty("authenticationFlow.exitOnError", Boolean.class, Boolean.FALSE);
    }

    @Override
    public void handle(RoutingContext context) {
        loadContext(context)
                .subscribe(
                        ctx -> {
                            // store the AuthenticationFlowContext in order to provide all related information about this context
                            context.put(ConstantKeys.AUTH_FLOW_CONTEXT_KEY, ctx);
                            // store only the AuthenticationFlowContext.data attributes in order to simplify EL templating
                            // and provide an up to date set of data if the enrichAuthFlow Policy is used multiple time in a step
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

    private Single<AuthenticationFlowContext> loadContext(RoutingContext context) {
        CookieSession session = (CookieSession) context.getDelegate().session();
        if (session == null || session.isDestroyed()) {
            return Single.error(new IllegalStateException("Session is missing"));
        }
        Identifier identifier = readIdentifier(session);
        return authenticationFlowContextService.loadContext(identifier.id, identifier.version);
    }

    private record Identifier(String id, int version) { }

    private Identifier readIdentifier(CookieSession session) {
        int version = ofNullable((Number) session.get(AUTH_FLOW_CONTEXT_VERSION_KEY))
                .map(Number::intValue)
                .orElse(1);
        String transactionId = session.get(ConstantKeys.TRANSACTION_ID_KEY);
        return new Identifier(requireNonNullElseGet(transactionId, () -> UUID.toString(UUID.random())), version);
    }
}
