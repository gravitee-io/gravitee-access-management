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
package io.gravitee.am.gateway.handler.aauth.resources.endpoint;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.aauth.service.bootstrap.AAuthBootstrapService;
import io.gravitee.am.gateway.handler.aauth.service.bootstrap.BootstrapTokenSigningException;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapRequest;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * POST /aauth/bootstrap-consent — processes the bootstrap consent form submission.
 * Approves or denies the bootstrap request based on user input and renders a
 * confirmation page (Thymeleaf templates {@code aauth_bootstrap_authorized} and
 * {@code aauth_bootstrap_denied}).
 *
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthBootstrapConsentPostEndpoint implements Handler<RoutingContext> {

    private static final String USER_APPROVAL_PARAM = "user_oauth_approval";

    private final AAuthBootstrapService bootstrapService;
    private final ThymeleafTemplateEngine engine;
    private final Domain domain;

    @Override
    public void handle(RoutingContext ctx) {
        MultiMap params = ctx.request().formAttributes();
        String code = params.get("code");

        if (code == null || code.isBlank()) {
            ctx.fail(new InvalidRequestException("Missing interaction code"));
            return;
        }

        io.gravitee.am.model.User user = ctx.user() != null
                ? ((User) ctx.user().getDelegate()).getUser()
                : null;

        if (user == null) {
            ctx.fail(new HttpException(401, "Authentication required"));
            return;
        }

        boolean approved = "true".equalsIgnoreCase(params.get(USER_APPROVAL_PARAM));

        bootstrapService.findByInteractionCode(code)
                .switchIfEmpty(Single.error(() -> new HttpException(410, "Invalid or expired interaction code")))
                .flatMapCompletable(request -> {
                    if (approved) {
                        return approveFlow(ctx, request, user);
                    } else {
                        return denyFlow(ctx, request);
                    }
                })
                .subscribe(
                        () -> { /* response already sent */ },
                        err -> {
                            if (err instanceof BootstrapTokenSigningException) {
                                // Misconfiguration: no asymmetric signing certificate.
                                // Log loudly with the stack trace so the operator notices.
                                log.error("Bootstrap consent approved but bootstrap_token cannot be signed: {}",
                                        err.getMessage(), err);
                            } else {
                                log.error("Error processing bootstrap consent: {}", err.getMessage());
                            }
                            ctx.fail(err);
                        }
                );
    }

    private Completable approveFlow(RoutingContext ctx, AAuthBootstrapRequest request,
                                     io.gravitee.am.model.User user) {
        String psIssuerUrl = resolvePsIssuerUrl(ctx);

        return bootstrapService.approve(request.getId(), user.getId(), psIssuerUrl)
                .flatMapCompletable(approved -> renderResult(ctx, approved, Template.AAUTH_BOOTSTRAP_AUTHORIZED));
    }

    private Completable denyFlow(RoutingContext ctx, AAuthBootstrapRequest request) {
        return bootstrapService.deny(request.getId())
                .flatMapCompletable(denied -> renderResult(ctx, denied, Template.AAUTH_BOOTSTRAP_DENIED));
    }

    private Completable renderResult(RoutingContext ctx, AAuthBootstrapRequest request, Template template) {
        ctx.put("agentServerName", request.getAgentServerName() != null
                ? request.getAgentServerName() : request.getAgentServerUrl());
        return engine.render(generateData(ctx, domain, null), template.template())
                .flatMapCompletable(buffer -> ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                        .rxEnd(buffer));
    }

    private String resolvePsIssuerUrl(RoutingContext ctx) {
        try {
            String basePath = UriBuilderRequest.resolveProxyRequest(ctx.request(), ctx.get(CONTEXT_PATH));
            return basePath + "/aauth";
        } catch (Exception e) {
            log.warn("Unable to resolve PS issuer URL from request context", e);
            return "/aauth";
        }
    }
}
