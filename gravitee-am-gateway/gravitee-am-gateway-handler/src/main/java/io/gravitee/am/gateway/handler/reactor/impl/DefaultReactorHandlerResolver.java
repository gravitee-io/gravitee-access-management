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
package io.gravitee.am.gateway.handler.reactor.impl;

import io.gravitee.am.gateway.handler.reactor.ReactorHandlerResolver;
import io.gravitee.am.gateway.handler.reactor.SecurityDomainHandlerRegistry;
import io.gravitee.am.gateway.handler.vertx.VertxSecurityDomainHandler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultReactorHandlerResolver implements ReactorHandlerResolver {

    private final Logger LOGGER = LoggerFactory.getLogger(DefaultReactorHandlerResolver.class);

    @Autowired
    private SecurityDomainHandlerRegistry handlerRegistry;

    @Override
    public VertxSecurityDomainHandler resolve(HttpServerRequest request) {
        StringBuilder path = new StringBuilder(request.path());

        if (path.charAt(path.length() - 1) != '/') {
            path.append('/');
        }

        String sPath = path.toString();

        VertxSecurityDomainHandler handler = null;
        for (VertxSecurityDomainHandler reactorHandler : handlerRegistry.getSecurityDomainHandlers()) {
            if (sPath.startsWith(reactorHandler.contextPath())) {
                handler = reactorHandler;
                break;
            }
        }

        if (handler != null) {
            LOGGER.debug("Returning the first handler matching path {} : {}", sPath, handler);
            return handler;
        }

        return null;
    }
}
