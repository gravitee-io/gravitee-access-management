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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.user;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Client implements User {

    private final String clientId;

    private final io.gravitee.am.model.oidc.Client client;

    public Client(io.gravitee.am.model.oidc.Client client) {
        this.clientId = client.getClientId();
        this.client = client;
    }

    public String getClientId() {
        return clientId;
    }

    public io.gravitee.am.model.oidc.Client getClient() {
        return client;
    }

    @Override
    public User isAuthorized(String s, Handler<AsyncResult<Boolean>> handler) {
        return null;
    }

    @Override
    public User clearCache() {
        return null;
    }

    @Override
    public JsonObject principal() {
        return null;
    }

    @Override
    public void setAuthProvider(AuthProvider authProvider) {

    }
}
