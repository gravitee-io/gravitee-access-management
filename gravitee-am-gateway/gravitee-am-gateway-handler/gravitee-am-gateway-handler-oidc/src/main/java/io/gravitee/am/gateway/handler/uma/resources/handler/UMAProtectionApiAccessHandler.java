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

import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.uma.exception.UMAProtectionApiForbiddenException;
import io.gravitee.am.model.Domain;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <pre>
 * This handler intend to verify if client is allowed to access to the UMA 2.0 Protection API.
 * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#resource-registration-endpoint">here</a>
 * </pre>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class UMAProtectionApiAccessHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UMAProtectionApiAccessHandler.class);
    private Domain domain;
    private OAuth2AuthHandler oAuth2AuthHandler;

    public UMAProtectionApiAccessHandler(Domain domain, OAuth2AuthHandler oAuth2AuthHandler) {
        this.domain = domain;
        this.oAuth2AuthHandler = oAuth2AuthHandler;
    }

    @Override
    public void handle(RoutingContext context) {
        if(domain.getUma()==null || !domain.getUma().isEnabled()) {
            LOGGER.debug("UMA 2.0 Resource registration is disabled");
            context.fail(new UMAProtectionApiForbiddenException());
            return;
        }
        this.oAuth2AuthHandler.handle(context);
    }
}
