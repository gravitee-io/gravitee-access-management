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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class WebAuthnEndpoint implements Handler<RoutingContext> {

    protected final UserAuthenticationManager userAuthenticationManager;

    WebAuthnEndpoint(UserAuthenticationManager userAuthenticationManager) {
        this.userAuthenticationManager = userAuthenticationManager;
    }

    /**
     * Check if a given user name exists
     * @param client OAuth 2.0 client
     * @param username User name
     * @param handler Response handler
     */
    protected void checkUser(Client client, String username, Handler<AsyncResult<User>> handler) {
        userAuthenticationManager.loadUserByUsername(client, username)
                .subscribe(
                        user -> handler.handle(Future.succeededFuture(user)),
                        error -> handler.handle(Future.failedFuture(error)),
                        () -> handler.handle(Future.failedFuture(new UsernameNotFoundException(username)))
                );
    }

    protected static boolean isEmptyString(JsonObject json, String key) {
        try {
            if (json == null) {
                return true;
            }
            if (!json.containsKey(key)) {
                return true;
            }
            String s = json.getString(key);
            return s == null || "".equals(s);
        } catch (RuntimeException e) {
            return true;
        }
    }

    protected static boolean isEmptyObject(JsonObject json, String key) {
        try {
            if (json == null) {
                return true;
            }
            if (!json.containsKey(key)) {
                return true;
            }
            JsonObject s = json.getJsonObject(key);
            return s == null;
        } catch (RuntimeException e) {
            return true;
        }
    }
}
