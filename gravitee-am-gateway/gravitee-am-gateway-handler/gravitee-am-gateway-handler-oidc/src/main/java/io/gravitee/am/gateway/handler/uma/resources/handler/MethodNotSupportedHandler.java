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
package io.gravitee.am.gateway.handler.uma.resources.handler;

import io.gravitee.am.common.exception.oauth2.MethodNotSupportedException;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * UMA Specification state that if the resource server request used an unsupported HTTP method,
 * the authorization server MUST respond with the HTTP 405 (Method Not Allowed) status code
 * and MAY respond with an unsupported_method_type error code.
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class MethodNotSupportedHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext context) {
        context.fail(new MethodNotSupportedException());
    }
}
