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

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentEndpoint implements Handler<RoutingContext> {
    private static final Logger logger = LoggerFactory.getLogger(UserConsentEndpoint.class);

    private final UserConsentService userConsentService;
    private final ThymeleafTemplateEngine engine;

    public UserConsentEndpoint(UserConsentService userConsentService, ThymeleafTemplateEngine engine) {
        this.userConsentService = userConsentService;
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final io.gravitee.am.model.User user = routingContext.user() != null ? ((User) routingContext.user().getDelegate()).getUser() : null;
        final String action = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().uri());
        final AuthorizationRequest authorizationRequest = routingContext.get(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY);
        final boolean prompt = authorizationRequest.getPrompts().contains("consent");

        // fetch scope information (name + description)
        fetchConsentInformation(authorizationRequest.getScopes(), prompt, client, user, h -> {
            if (h.failed()) {
                routingContext.fail(h.cause());
                return;
            }
            List<Scope> requestedScopes = h.result();
            routingContext.put(ConstantKeys.SCOPES_CONTEXT_KEY, requestedScopes);
            routingContext.put(ConstantKeys.ACTION_KEY, action);
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

    private void fetchConsentInformation(Set<String> requestedConsents, boolean prompt, Client client, io.gravitee.am.model.User user, Handler<AsyncResult<List<Scope>>> handler) {

        final Single<List<Scope>> consentInformation;

        if (prompt) {
            consentInformation = userConsentService.getConsentInformation(requestedConsents);
        } else {
            consentInformation = userConsentService.checkConsent(client, user)
                    .flatMap(approvedConsent -> {
                        // user approved consent, continue
                        if (approvedConsent.containsAll(requestedConsents)) {
                            //redirectToAuthorize
                            return Single.just(Collections.<Scope>emptyList());
                        }
                        // else go to the user consent page
                        Set<String> requiredConsent = requestedConsents.stream().filter(requestedScope -> !approvedConsent.contains(requestedScope)).collect(Collectors.toSet());

                        return userConsentService.getConsentInformation(requiredConsent);
                    });
        }

        consentInformation.subscribe(
                scopes -> handler.handle(Future.succeededFuture(scopes)),
                error -> handler.handle(Future.failedFuture(error)));
    }

    private String getTemplateFileName(Client client) {
        return Template.OAUTH2_USER_CONSENT.template() + (client != null ? "|" + client.getId() : "");
    }
}
