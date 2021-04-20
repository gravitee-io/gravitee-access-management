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
package io.gravitee.am.gateway.handler.users.resources.consents;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.users.service.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentsEndpointHandler extends AbstractUserConsentEndpointHandler {

    public UserConsentsEndpointHandler(UserService userService, ClientSyncService clientSyncService, Domain domain) {
        super(userService, clientSyncService, domain);
    }

    /**
     * Retrieve consents for a user per application basis or for all applications
     */
    public void list(RoutingContext context) {
        final String userId = context.request().getParam("userId");
        final String clientId = context.request().getParam("clientId");

        Single
            .just(Optional.ofNullable(clientId))
            .flatMap(
                optClient -> {
                    if (optClient.isPresent()) {
                        return userService.consents(userId, optClient.get());
                    }
                    return userService.consents(userId);
                }
            )
            .subscribe(
                scopeApprovals ->
                    context
                        .response()
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .end(Json.encodePrettily(scopeApprovals)),
                error -> context.fail(error)
            );
    }

    /**
     * Revoke consents for a user per application basis or for all applications
     */
    public void revoke(RoutingContext context) {
        final String userId = context.request().getParam("userId");
        final String clientId = context.request().getParam("clientId");

        Single
            .just(Optional.ofNullable(clientId))
            .flatMapCompletable(
                optClient -> {
                    if (optClient.isPresent()) {
                        return getPrincipal(context)
                            .flatMapCompletable(principal -> userService.revokeConsents(userId, optClient.get(), principal));
                    }
                    return getPrincipal(context).flatMapCompletable(principal -> userService.revokeConsents(userId, principal));
                }
            )
            .subscribe(() -> context.response().setStatusCode(204).end(), error -> context.fail(error));
    }
}
