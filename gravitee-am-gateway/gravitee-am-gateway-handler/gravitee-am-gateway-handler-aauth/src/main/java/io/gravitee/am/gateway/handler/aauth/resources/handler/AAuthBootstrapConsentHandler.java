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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.aauth.service.bootstrap.AAuthBootstrapService;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapRequest;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Handles GET /aauth/bootstrap-consent — renders the bootstrap consent page.
 * Reads the bootstrap request from the routing context (placed by the interaction
 * resolve handler) and renders the {@code aauth_bootstrap_consent} Thymeleaf template.
 *
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthBootstrapConsentHandler implements Handler<RoutingContext> {

    private final AAuthBootstrapService bootstrapService;
    private final ThymeleafTemplateEngine engine;
    private final Domain domain;

    @Override
    public void handle(RoutingContext ctx) {
        String code = ctx.request().getParam("code");
        if (code == null || code.isBlank()) {
            ctx.fail(new InvalidRequestException("Missing interaction code"));
            return;
        }

        io.gravitee.am.model.User user = ctx.user() != null
                ? ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) ctx.user().getDelegate()).getUser()
                : null;

        if (user == null) {
            ctx.fail(new HttpException(401, "Authentication required"));
            return;
        }

        bootstrapService.findByInteractionCode(code)
                .switchIfEmpty(Single.error(() -> new HttpException(410, "Invalid or expired interaction code")))
                .flatMapCompletable(request -> renderConsentPage(ctx, request))
                .subscribe(
                        () -> { /* response already sent */ },
                        err -> {
                            log.error("Error in bootstrap consent flow: {}", err.getMessage());
                            ctx.fail(err);
                        }
                );
    }

    private io.reactivex.rxjava3.core.Completable renderConsentPage(RoutingContext ctx, AAuthBootstrapRequest request) {
        String action = resolveConsentUrl(ctx);

        ctx.put("agentServerName", request.getAgentServerName() != null
                ? request.getAgentServerName() : request.getAgentServerUrl());
        ctx.put("agentServerLogoUri", request.getAgentServerLogoUri());
        ctx.put("interactionCode", request.getInteractionCode());
        ctx.put(ConstantKeys.ACTION_KEY, action);

        String templateName = Template.AAUTH_BOOTSTRAP_CONSENT.template();

        return engine.render(generateData(ctx, domain, null), templateName)
                .flatMapCompletable(buffer -> ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                        .end(buffer));
    }

    private String resolveConsentUrl(RoutingContext ctx) {
        try {
            return UriBuilderRequest.resolveProxyRequest(ctx.request(), ctx.get(CONTEXT_PATH))
                    + "/aauth/bootstrap-consent";
        } catch (Exception e) {
            return "/aauth/bootstrap-consent";
        }
    }
}
