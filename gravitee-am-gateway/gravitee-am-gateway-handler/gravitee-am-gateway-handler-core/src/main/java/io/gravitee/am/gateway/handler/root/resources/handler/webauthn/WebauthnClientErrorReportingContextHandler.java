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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.common.utils.ConstantKeys.WEBAUTHN_CLIENT_ERROR_REPORTING_ENABLED_KEY;

/**
 * Exposes {@link ConstantKeys#WEBAUTHN_CLIENT_ERROR_REPORTING_ENABLED_KEY} to Thymeleaf pages (login, register, MFA FIDO2).
 *
 * @author GraviteeSource Team
 */
public class WebauthnClientErrorReportingContextHandler implements Handler<RoutingContext> {

    private final boolean enabled;

    public WebauthnClientErrorReportingContextHandler(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.put(WEBAUTHN_CLIENT_ERROR_REPORTING_ENABLED_KEY, enabled);
        routingContext.next();
    }
}
