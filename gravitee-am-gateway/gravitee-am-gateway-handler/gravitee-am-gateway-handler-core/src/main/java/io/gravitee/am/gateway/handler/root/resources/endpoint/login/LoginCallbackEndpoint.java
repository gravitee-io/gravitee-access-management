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
package io.gravitee.am.gateway.handler.root.resources.endpoint.login;

import com.google.common.net.HttpHeaders;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.common.utils.ConstantKeys.ID_TOKEN_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PARAM_CONTEXT_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackEndpoint implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext routingContext) {
        final Session session = routingContext.session();
        final String returnURL = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/oauth/authorize", (MultiMap) routingContext.get(PARAM_CONTEXT_KEY), true);

        // if we have an id_token, put in the session context for post step (mainly the user consent step)
        if (session != null) {
            if (routingContext.data().containsKey(ID_TOKEN_KEY)) {
                session.put(ID_TOKEN_KEY, routingContext.get(ID_TOKEN_KEY));
            }
            // save that the user has just been signed in
            session.put(ConstantKeys.USER_LOGIN_COMPLETED_KEY, true);
        }

        // the login process is done
        // redirect the user to the original request
        doRedirect(routingContext.response(), returnURL);
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }
}
