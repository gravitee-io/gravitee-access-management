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
package io.gravitee.am.gateway.handler.root.resources.endpoint.mfa;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class MFAEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    protected MFAEndpoint(TemplateEngine templateEngine) {
        super(templateEngine);
    }

    protected final boolean hasApplicationFactorSettings(Client client) {
        return client.getFactorSettings() != null && client.getFactorSettings().getApplicationFactors() != null;
    }

    protected User user(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        if (user != null) {
            return user;
        }
        if (routingContext.user() != null) {
            return ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
        }
        return null;
    }
}
