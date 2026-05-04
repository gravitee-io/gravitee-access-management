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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus;
import io.gravitee.am.gateway.handler.aauth.service.bootstrap.AAuthBootstrapService;
import io.gravitee.am.gateway.handler.aauth.service.registry.AAuthAgentRegistry;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Resolves the bootstrap request by interaction code and prepares the routing
 * context for the authentication flow, then redirects to the bootstrap consent page.
 * <p>
 * The agent server URL from the bootstrap request is used to resolve (or auto-create)
 * the Application via {@link AAuthAgentRegistry}, providing the client_id needed by
 * the authentication flow.
 * <p>
 * Runs BEFORE the authentication flow handler in the GET /aauth/bootstrap-interact chain.
 * After authentication completes, {@link AAuthBootstrapConsentRedirectHandler} redirects
 * to /aauth/bootstrap-consent where the consent handler renders the approval page.
 *
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthBootstrapInteractHandler implements Handler<RoutingContext> {

    public static final String AAUTH_BOOTSTRAP_CONTEXT_KEY = "aauth.bootstrap";

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            PendingRequestStatus.EXPIRED.name(),
            PendingRequestStatus.DENIED.name(),
            PendingRequestStatus.COMPLETED.name());

    private final AAuthBootstrapService bootstrapService;
    private final AAuthAgentRegistry agentRegistry;
    private final String domainId;

    @Override
    public void handle(RoutingContext ctx) {
        String code = ctx.request().getParam("code");
        if (code == null || code.isBlank()) {
            ctx.fail(new InvalidRequestException("Missing interaction code"));
            return;
        }

        bootstrapService.findByInteractionCode(code)
                .switchIfEmpty(Maybe.error(() -> new HttpException(410, "Invalid or expired interaction code")))
                .filter(request -> !TERMINAL_STATUSES.contains(request.getStatus()))
                .switchIfEmpty(Maybe.error(() -> new HttpException(410, "This bootstrap request has expired or already been processed")))
                .doOnSuccess(request -> {
                    ctx.put(AAUTH_BOOTSTRAP_CONTEXT_KEY, request);
                    if (ctx.session() != null) {
                        ctx.session().put(AAUTH_BOOTSTRAP_CONTEXT_KEY, request);
                    }
                    String returnUrl = UriBuilderRequest.resolveProxyRequest(
                            ctx.request(), ctx.get(CONTEXT_PATH) + "/aauth/bootstrap-interact",
                            io.vertx.core.MultiMap.caseInsensitiveMultiMap().add("code", code), true);
                    ctx.put(ConstantKeys.RETURN_URL_KEY, returnUrl);
                    if (request.getLoginHint() != null) {
                        ctx.put(io.gravitee.am.common.oidc.Parameters.LOGIN_HINT, request.getLoginHint());
                    }
                })
                .flatMapSingle(this::resolveOrCreateApplication)
                .doOnSuccess(client -> {
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.put(Parameters.CLIENT_ID, client.getClientId());
                    if (ctx.session() != null) {
                        ctx.session().put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    }
                })
                .ignoreElement()
                .subscribe(
                        ctx::next,
                        err -> {
                            log.error("Error resolving bootstrap interaction: {}", err.getMessage());
                            ctx.fail(err);
                        }
                );
    }

    /**
     * Resolve or auto-create the Application for the bootstrap request's agent server.
     * Uses the same AAuthAgentRegistry as the authorization flow, which handles
     * auto-registration of AAUTH_AGENT applications on first contact.
     */
    private Single<Client> resolveOrCreateApplication(AAuthBootstrapRequest request) {
        String agentServerUrl = request.getAgentServerUrl();
        if (agentServerUrl == null) {
            return Single.error(new InvalidRequestException("Bootstrap request has no agent_server"));
        }
        // Build a minimal VerificationResult with just the agentServerUrl
        // so the registry can look up or auto-create the Application
        VerificationResult verification = new VerificationResult(
                "hwk", "sig", null, null, agentServerUrl, null);
        return agentRegistry.resolveOrCreate(verification, domainId)
                .switchIfEmpty(Single.error(() -> new InvalidRequestException(
                        "Could not resolve or create application for agent server " + agentServerUrl)))
                .map(Application::toClient);
    }
}
