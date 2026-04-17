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
package io.gravitee.am.gateway.handler.aauth.resources.handler;

import io.gravitee.am.gateway.handler.aauth.service.registry.AAuthAgentRegistry;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Vert.x handler that resolves the {@code Application(type=AAUTH_AGENT)} for the
 * verified agent and stashes it in the routing context as "aauth.application".
 * <p>
 * Runs after {@link AAuthSignatureHandler} in the handler chain. If the signature
 * verification produced an agent identity URL (jwks_uri/jwt scheme), the registry
 * looks up or auto-creates the Application. For pseudonymous (hwk) requests,
 * the routing context entry is left absent and the handler calls {@code next()}.
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthAgentResolveHandler implements Handler<RoutingContext> {

    public static final String AAUTH_APPLICATION_CONTEXT_KEY = "aauth.application";

    private final AAuthAgentRegistry registry;
    private final String domainId;

    @Override
    public void handle(RoutingContext ctx) {
        VerificationResult verification = ctx.get(AAuthSignatureHandler.AAUTH_VERIFICATION_CONTEXT_KEY);

        if (verification == null || verification.agentServerUrl() == null) {
            // No signature or pseudonymous — no Application to resolve
            ctx.next();
            return;
        }

        registry.resolveOrCreate(verification, domainId)
                .subscribe(
                        app -> {
                            log.debug("Resolved AAUTH agent Application: id={}, clientId={}",
                                    app.getId(), app.clientId());
                            ctx.put(AAUTH_APPLICATION_CONTEXT_KEY, app);
                            ctx.next();
                        },
                        err -> {
                            log.error("Failed to resolve AAUTH agent: {}", err.getMessage());
                            ctx.response().setStatusCode(500).end();
                        },
                        () -> {
                            // Empty = pseudonymous mode
                            ctx.next();
                        }
                );
    }
}
