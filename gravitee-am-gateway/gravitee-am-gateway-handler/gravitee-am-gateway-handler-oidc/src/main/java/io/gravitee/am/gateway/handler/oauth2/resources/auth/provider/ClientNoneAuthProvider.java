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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.provider;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;

/**
 * Client Authentication method : none
 *
 * The Client does not authenticate itself at the Token Endpoint, either because it uses only the Implicit Flow (and so does not use the Token Endpoint)
 * or because it is a Public Client with no Client Secret or other authentication mechanism.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientNoneAuthProvider implements ClientAuthProvider {

    @Override
    public boolean canHandle(Client client, HttpServerRequest request) {
        if (GrantType.CLIENT_CREDENTIALS.equals(request.getParam(Parameters.GRANT_TYPE))) {
            return false;
        }
        return client != null && ClientAuthenticationMethod.NONE.equals(client.getTokenEndpointAuthMethod());
    }

    @Override
    public void handle(Client client, HttpServerRequest request, Handler<AsyncResult<Client>> handler) {
        handler.handle(Future.succeededFuture(client));
    }
}
