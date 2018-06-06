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
package io.gravitee.am.gateway.handler.vertx.handler.login.endpoint;

import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LogoutEndpointHandler implements Handler<RoutingContext> {

    private static final String LOGOUT_URL_PARAMETER = "target_url";
    private static final String DEFAULT_TARGET_URL = "/";

    @Override
    public void handle(RoutingContext routingContext) {
        // clear context and session
        routingContext.clearUser();
        if (routingContext.session() != null) {
            routingContext.session().destroy();
        }

        // redirect to target url
        String logoutRedirectUrl = routingContext.request().getParam(LOGOUT_URL_PARAMETER);
        routingContext
                .response()
                .putHeader(HttpHeaders.LOCATION, (logoutRedirectUrl != null && !logoutRedirectUrl.isEmpty()) ? logoutRedirectUrl : DEFAULT_TARGET_URL)
                .setStatusCode(302)
                .end();
    }

    public static LogoutEndpointHandler create() {
        return new LogoutEndpointHandler();
    }
}
