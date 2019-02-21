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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization;

import io.gravitee.am.gateway.handler.form.FormManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.scope.ScopeService;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.ThymeleafTemplateEngine;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserApprovalEndpointHandler implements Handler<RoutingContext>  {

    private ThymeleafTemplateEngine engine;
    private ClientSyncService clientSyncService;
    private ScopeService scopeService;

    public UserApprovalEndpointHandler(ClientSyncService clientSyncService, ScopeService scopeService, ThymeleafTemplateEngine engine) {
        this.clientSyncService = clientSyncService;
        this.scopeService = scopeService;
        this.engine = engine;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // TODO use repository instead ?
        AuthorizationRequest authorizationRequest = routingContext.session().get(OAuth2Constants.AUTHORIZATION_REQUEST);

        if (authorizationRequest == null) {
            routingContext.response().setStatusCode(400).end("An authorization request is required to handle user approval");
            return;
        }

        clientSyncService.findByClientId(authorizationRequest.getClientId())
                .toSingle()
                .zipWith(scopeService.getAll(), (client, domainScopes) -> {
                    // fetch scope information (name + description) from the authorization request
                    Set<Scope> requestedScopes = new HashSet<>();
                    for (String requestScope : authorizationRequest.getScopes()) {
                        Scope requestedScope = domainScopes
                                .stream()
                                .filter(scope -> scope.getKey().equalsIgnoreCase(requestScope))
                                .findAny()
                                .orElse(new Scope(requestScope));

                        requestedScopes.add(requestedScope);
                    }
                    return new ApprovalData(client, requestedScopes);
                })
                .subscribe(approvalData -> {
                        routingContext.put("client", approvalData.getClient());
                        routingContext.put("scopes", approvalData.getScopes());
                        engine.render(routingContext, getTemplateFileName(approvalData.getClient()), res -> {
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

    public void setClientSyncService(ClientSyncService clientSyncService) {
        this.clientSyncService = clientSyncService;
    }

    private String getTemplateFileName(Client client) {
        return "oauth2_user_consent" + (client != null ? FormManager.TEMPLATE_NAME_SEPARATOR + client.getId() : "");
    }

    private class ApprovalData {
        private Client client;
        private Set<Scope> scopes;

        public ApprovalData(Client client, Set<Scope> scopes) {
            this.client = new Client();
            this.client.setId(client.getId());
            this.client.setClientId(client.getClientId());
            this.client.setClientName(client.getClientName());
            this.scopes = scopes;
        }

        public Client getClient() {
            return client;
        }

        public void setClient(Client client) {
            this.client = client;
        }

        public Set<Scope> getScopes() {
            return scopes;
        }

        public void setScopes(Set<Scope> scopes) {
            this.scopes = scopes;
        }
    }
}
