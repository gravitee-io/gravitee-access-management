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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailDigest;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailTypeManager;
import io.gravitee.am.gateway.handler.oauth2.service.consent.AuthorizationDetailsConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.oauth2.AuthorizationDetail;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.AuthorizationDetailTypeProperties;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import lombok.CustomLog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;

@CustomLog
public class AuthorizationDetailsConsentEndpoint implements Handler<RoutingContext> {

    private final AuthorizationDetailsConsentService authorizationDetailsConsentService;
    private final AuthorizationDetailTypeManager authorizationDetailTypeManager;
    private final ThymeleafTemplateEngine engine;
    private final Domain domain;

    public AuthorizationDetailsConsentEndpoint(AuthorizationDetailsConsentService authorizationDetailsConsentService,
                                               AuthorizationDetailTypeManager authorizationDetailTypeManager,
                                               ThymeleafTemplateEngine engine, Domain domain) {
        this.authorizationDetailsConsentService = authorizationDetailsConsentService;
        this.authorizationDetailTypeManager = authorizationDetailTypeManager;
        this.engine = engine;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final io.gravitee.am.model.User user = ((User) routingContext.user().getDelegate()).getUser();
        final AuthorizationRequest authorizationRequest = routingContext.get(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY);
        final boolean promptConsent = authorizationRequest.getPrompts() != null && authorizationRequest.getPrompts().contains("consent");
        final AuthorizationDetails requested = authorizationRequest.getAuthorizationDetails() == null
                ? AuthorizationDetails.of(List.of())
                : authorizationRequest.getAuthorizationDetails();

        authorizationDetailsConsentService.getDetailsToPresent(client, user, requested, promptConsent)
                .subscribe(presented -> {
                    routingContext.session().put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PRESENTED_KEY, AuthorizationDetailDigest.ofAll(presented));
                    // the template only gets the details it presents
                    routingContext.put(ConstantKeys.AUTHORIZATION_DETAILS_CONTEXT_KEY, AuthorizationDetails.of(presented));
                    routingContext.put(ConstantKeys.AUTHORIZATION_DETAIL_TYPES_CONTEXT_KEY, typesOf(presented));
                    routingContext.put(ConstantKeys.ACTION_KEY, UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.request().uri()));
                    CimdConsentPageAttributes.putIfApplicable(routingContext, domain, client);
                    engine.render(generateData(routingContext, domain, client), getTemplateFileName(client))
                            .subscribe(buffer -> {
                                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                                routingContext.response().end(buffer);
                            }, throwable -> {
                                log.error("Unable to render authorization detail consent page", throwable);
                                routingContext.fail(throwable.getCause());
                            });
                }, routingContext::fail);
    }

    private Map<String, AuthorizationDetailTypeProperties> typesOf(List<AuthorizationDetail> details) {
        final Map<String, AuthorizationDetailTypeProperties> types = new HashMap<>();
        details.stream()
                .map(AuthorizationDetail::getType)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(type -> authorizationDetailTypeManager.findByType(type)
                        .ifPresent(found -> types.put(type, new AuthorizationDetailTypeProperties(found))));
        return types;
    }

    private static String getTemplateFileName(Client client) {
        return Template.OAUTH2_AUTHORIZATION_DETAILS_CONSENT.template() + (client != null ? "|" + client.getId() : "");
    }
}
