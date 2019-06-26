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
package io.gravitee.am.gateway.reactor.impl;

import io.gravitee.am.gateway.handler.SecurityDomainRouterFactory;
import io.gravitee.am.gateway.handler.vertx.VertxSecurityDomainHandler;
import io.gravitee.am.gateway.reactor.Reactor;
import io.gravitee.am.gateway.reactor.SecurityDomainHandlerRegistry;
import io.gravitee.am.model.Domain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultSecurityDomainHandlerRegistry implements SecurityDomainHandlerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSecurityDomainHandlerRegistry.class);
    private final ConcurrentMap<String, VertxSecurityDomainHandler> handlers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, String> contextPaths = new ConcurrentHashMap<>();

    @Autowired
    private SecurityDomainRouterFactory securityDomainRouterFactory;

    @Autowired
    private Reactor reactor;

    @Override
    public void create(Domain domain) {
        logger.info("Register a new domain for {} on path {}", domain.getId(), domain.getPath());

        VertxSecurityDomainHandler handler = create0(domain);
        if (handler != null) {
            try {
                handler.start();
                handlers.putIfAbsent(handler.contextPath(), handler);
                contextPaths.putIfAbsent(domain, handler.contextPath());
                reactor.mountSubRouter(handler.contextPath(), handler.router());
            } catch (Exception ex) {
                logger.error("Unable to register handler", ex);
            }
        }
    }

    @Override
    public void update(Domain domain) {
        String contextPath = contextPaths.get(domain);
        if (contextPath != null) {
            VertxSecurityDomainHandler handler = handlers.get(contextPath);
            if (handler != null) {
                remove(domain);
                create(domain);
            }
        } else {
            create(domain);
        }
    }

    @Override
    public void remove(Domain domain) {
        String contextPath = contextPaths.remove(domain);
        if (contextPath != null) {
            VertxSecurityDomainHandler handler = handlers.remove(contextPath);

            if (handler != null) {
                try {
                    handler.stop();
                    handlers.remove(handler.contextPath());
                    reactor.unMountSubRouter(handler.contextPath());
                    logger.info("Security Domain has been unregistered");
                } catch (Exception e) {
                    logger.error("Unable to un-register handler", e);
                }
            }
        }
    }

    @Override
    public void clear() {
        handlers.forEach((s, handler) -> {
            try {
                handler.stop();
                handlers.remove(handler.contextPath());
            } catch (Exception e) {
                logger.error("Unable to un-register handler", e);
            }
        });
        contextPaths.clear();
    }

    @Override
    public Collection<VertxSecurityDomainHandler> getSecurityDomainHandlers() {
        return handlers.values();
    }

    private VertxSecurityDomainHandler create0(Domain domain) {
        return securityDomainRouterFactory.create(domain);
    }

}
