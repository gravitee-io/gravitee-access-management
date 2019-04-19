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
package io.gravitee.am.gateway.handler.oidc.resources.handler;

import io.gravitee.am.gateway.handler.oidc.exception.ClientRegistrationForbiddenException;
import io.gravitee.am.model.Domain;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamic Client Registration is a protocol that allows OAuth client applications to register with an OAuth server.
 * Specifications are defined by OpenID Foundation and by the IETF as RFC 7591 too.
 * They define how a client may submit a request to register itself and how should be the response.
 *
 * See <a href="https://openid.net/specs/openid-connect-registration-1_0.html">Openid Connect Dynamic Client Registration</a>
 * See <a href="https://tools.ietf.org/html/rfc7591"> OAuth 2.0 Dynamic Client Registration Protocol</a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class DynamicClientRegistrationHandler implements Handler<RoutingContext> {

    private Domain domain;

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicClientRegistrationHandler.class);

    public DynamicClientRegistrationHandler(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {

        //Do not apply security check if open dynamic client registration is enabled.
        if(domain.isOpenDynamicClientRegistrationEnabled()) {
            LOGGER.debug("Open Dynamic client registration is enabled - no security will be performed.");
            context.next();
            return;
        }

        //1st check if dynamic client registration is enabled.
        if(!domain.isDynamicClientRegistrationEnabled()) {
            LOGGER.debug("Dynamic client registration is disabled");
            context.fail(new ClientRegistrationForbiddenException());
            return;
        }

        context.next();
    }
}
