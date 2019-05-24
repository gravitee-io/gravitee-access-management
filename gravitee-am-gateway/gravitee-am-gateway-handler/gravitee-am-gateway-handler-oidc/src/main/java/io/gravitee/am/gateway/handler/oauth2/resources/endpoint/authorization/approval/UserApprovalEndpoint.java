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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.approval;

import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeService;
import io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserApprovalEndpoint implements Handler<RoutingContext>  {

    private static final String CLIENT_CONTEXT_KEY = "client";
    private ThymeleafTemplateEngine engine;
    private ScopeService scopeService;

    public UserApprovalEndpoint(ScopeService scopeService, ThymeleafTemplateEngine engine) {
        this.scopeService = scopeService;
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // retrieve client
        Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        // retrieve authorization request
        AuthorizationRequest authorizationRequest = routingContext.session().get(OAuth2Constants.AUTHORIZATION_REQUEST);

        // fetch scope information (name + description) from the authorization request
        scopeService.getAll()
                .map(domainScopes -> {
                    // fetch scope information (name + description) from the authorization request
                    Set<Scope> requestedScopes = new HashSet<>();
                    for (String requestScope : authorizationRequest.getDeniedScopes()) {
                        Scope requestedScope = domainScopes
                                .stream()
                                .filter(scope -> scope.getKey().equalsIgnoreCase(requestScope))
                                .findAny()
                                .orElse(new Scope(requestScope));

                        requestedScopes.add(requestedScope);
                    }
                    return requestedScopes;
                })
                .subscribe(
                        requestedScopes -> {
                            Client safeClient = safeClient(client);
                            routingContext.put(CLIENT_CONTEXT_KEY, safeClient);
                            routingContext.put("scopes", requestedScopes);
                            engine.render(routingContext.data(), getTemplateFileName(safeClient), res -> {
                                if (res.succeeded()) {
                                    routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                                    routingContext.response().end(res.result());
                                } else {
                                   routingContext.fail(res.cause());
                                }
                            });
                        },
                        error -> routingContext.fail(error));
    }

    private String getTemplateFileName(Client client) {
       return "oauth2_user_consent" + (client != null ? "|" + client.getId() : "");
    }

    private Client safeClient(Client client) {
        Client safeClient = new Client();
        safeClient.setId(client.getId());
        safeClient.setClientId(client.getClientId());
        safeClient.setClientName(client.getClientName());
        return safeClient;
    }
}
