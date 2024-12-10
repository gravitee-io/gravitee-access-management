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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;

import static io.gravitee.am.gateway.handler.common.vertx.utils.RedirectHelper.doRedirect;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginPostEndpoint extends AbstractEndpoint implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext context) {
        final Session session = context.session();

        // save that the user has just been signed in
        session.put(ConstantKeys.USER_LOGIN_COMPLETED_KEY, true);

        // the login process is done
        // redirect the user to the original request
        doRedirect(context);
    }
}
