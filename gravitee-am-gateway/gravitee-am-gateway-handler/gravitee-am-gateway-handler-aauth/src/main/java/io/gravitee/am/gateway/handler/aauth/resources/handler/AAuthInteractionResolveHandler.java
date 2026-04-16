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
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
import io.gravitee.am.service.ApplicationService;
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
 * Resolves the pending request by interaction code and sets the Application
 * in {@code CLIENT_CONTEXT_KEY} so that the login flow (IdP selection, MFA,
 * etc.) is configured for this specific agent.
 * <p>
 * Runs BEFORE the authentication flow handler in the GET /aauth/interact chain.
 * After authentication completes, {@link AAuthConsentRedirectHandler} redirects
 * to /aauth/consent where the consent handler reads the pending request from
 * the session.
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthInteractionResolveHandler implements Handler<RoutingContext> {

    public static final String AAUTH_PENDING_CONTEXT_KEY = "aauth.pending";

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            PendingRequestStatus.EXPIRED.name(),
            PendingRequestStatus.DENIED.name(),
            PendingRequestStatus.COMPLETED.name());

    private final AAuthPendingRequestService pendingService;
    private final ApplicationService applicationService;

    @Override
    public void handle(RoutingContext ctx) {
        String code = ctx.request().getParam("code");
        if (code == null || code.isBlank()) {
            ctx.fail(new InvalidRequestException("Missing interaction code"));
            return;
        }

        pendingService.findByInteractionCode(code)
                .switchIfEmpty(Maybe.error(() -> new HttpException(410, "Invalid or expired interaction code")))
                .filter(pending -> !TERMINAL_STATUSES.contains(pending.getStatus()))
                .switchIfEmpty(Maybe.error(() -> new HttpException(410, "This authorization request has expired or already been processed")))
                .doOnSuccess(pending -> {
                    ctx.put(AAUTH_PENDING_CONTEXT_KEY, pending);
                    if (ctx.session() != null) {
                        ctx.session().put(AAUTH_PENDING_CONTEXT_KEY, pending);
                    }
                    String returnUrl = UriBuilderRequest.resolveProxyRequest(
                            ctx.request(), ctx.get(CONTEXT_PATH) + "/aauth/interact",
                            io.vertx.core.MultiMap.caseInsensitiveMultiMap().add("code", code), true);
                    ctx.put(ConstantKeys.RETURN_URL_KEY, returnUrl);
                })
                .flatMapSingle(this::resolveApplication)
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
                            log.error("Error resolving interaction: {}", err.getMessage());
                            ctx.fail(err);
                        }
                );
    }

    private Single<Client> resolveApplication(AAuthPendingRequest pending) {
        if (pending.getApplicationId() == null) {
            return Single.error(new InvalidRequestException("Agent was not identified"));
        }
        return applicationService.findById(pending.getApplicationId())
                .switchIfEmpty(Single.error(() -> new InvalidRequestException(
                        "No client found for client_id " + pending.getApplicationId())))
                .map(Application::toClient);
    }
}
