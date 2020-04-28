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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.consent;

import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentEndpoint implements Handler<RoutingContext> {
    private static final Logger logger = LoggerFactory.getLogger(UserConsentEndpoint.class);
    private static final String CLIENT_CONTEXT_KEY = "client";
    private static final String SCOPES_CONTEXT_KEY = "scopes";
    private static final String REQUESTED_CONSENT_CONTEXT_KEY = "requestedConsent";
    private UserConsentService userConsentService;
    private ThymeleafTemplateEngine engine;

    public UserConsentEndpoint(UserConsentService userConsentService, ThymeleafTemplateEngine engine) {
        this.userConsentService = userConsentService;
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Session session = routingContext.session();
        final Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        final Set<String> requiredConsent = session.get(REQUESTED_CONSENT_CONTEXT_KEY);

        // fetch scope information (name + description)
        fetchConsentInformation(requiredConsent, h -> {
            if (h.failed()) {
                routingContext.fail(h.cause());
                return;
            }
            List<Scope> requestedScopes = h.result();
            routingContext.put(SCOPES_CONTEXT_KEY, requestedScopes);
            engine.render(routingContext.data(), getTemplateFileName(client), res -> {
                if (res.succeeded()) {
                    routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                    routingContext.response().end(res.result());
                } else {
                    logger.error("Unable to render user consent page", res.cause());
                    routingContext.fail(res.cause());
                }
            });
        });
    }

    private void fetchConsentInformation(Set<String> requiredConsent, Handler<AsyncResult<List<Scope>>> handler) {
        userConsentService.getConsentInformation(requiredConsent)
                .subscribe(
                        scopes -> handler.handle(Future.succeededFuture(scopes)),
                        error -> handler.handle(Future.failedFuture(error)));
    }

    private String getTemplateFileName(Client client) {
        return Template.OAUTH2_USER_CONSENT.template() + (client != null ? "|" + client.getId() : "");
    }
}
