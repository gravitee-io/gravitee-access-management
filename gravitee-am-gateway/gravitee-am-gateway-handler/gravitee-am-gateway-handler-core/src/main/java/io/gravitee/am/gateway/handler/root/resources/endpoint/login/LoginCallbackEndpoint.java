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
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.RedirectAuthHandler;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackEndpoint implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext routingContext) {
        Session session = routingContext.session();
        if (session != null && session.get(RedirectAuthHandler.DEFAULT_RETURN_URL_PARAM) != null) {
            final String redirectUrl = session.get(RedirectAuthHandler.DEFAULT_RETURN_URL_PARAM);
            doRedirect(routingContext.response(), redirectUrl);
        } else {
            routingContext.fail(503);
        }

    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }
}
