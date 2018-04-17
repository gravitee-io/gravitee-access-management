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
package io.gravitee.am.gateway.handler.vertx.oauth2.endpoint;

import io.gravitee.am.model.Domain;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.ThymeleafTemplateEngine;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginEndpointHandler implements Handler<RoutingContext> {

    final ThymeleafTemplateEngine engine = ThymeleafTemplateEngine.create();

    private Domain domain;

    public LoginEndpointHandler() {}

    public LoginEndpointHandler(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // TODO get login custom form
        routingContext.put("domain", domain);
        engine.render(routingContext, "webroot/views/login.html", res -> {
            if (res.succeeded()) {
                routingContext.response().end(res.result());
            } else {
                routingContext.fail(res.cause());
            }
        });
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }
}
