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
package io.gravitee.am.gateway.jetty.handler.security;

import io.gravitee.am.definition.Domain;
import io.gravitee.am.gateway.core.event.DomainEvent;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SecurityDomainHandlerCollection extends HandlerWrapper implements Handler, EventListener<DomainEvent, Domain> {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(SecurityDomainHandlerCollection.class);

    @Autowired
    private SecurityDomainHandlerFactory domainHandlerFactory;

    @Autowired
    private EventManager eventManager;

    private final ContextHandlerCollection parent = new ContextHandlerCollection();

    public SecurityDomainHandlerCollection() {
        setHandler(parent);
    }

    @Override
    public void onEvent(Event<DomainEvent, Domain> event) {
        logger.debug("An event has been received: [{} - {}]", event.content().getName(), event.type());

        switch (event.type()) {
            case DEPLOY:
                create(event.content());
                break;
            case UNDEPLOY:
                update(event.content());
                break;
        }
    }

    public void create(Domain domain) {
        // API is added only if flag as enabled.
        if (domain.isEnabled()) {
            addHandler(domain);
        } else {
            logger.warn("Domain {} is disabled !", domain);
        }
    }

    public void update(Domain domain) {
        remove(domain);
        create(domain);
    }

    public void remove(Domain domain) {
        removeHandler(domain);
    }

    public void addHandler(final Domain domain) {
        ContextHandler handler = domainHandlerFactory.create(domain);

        try {
            parent.addHandler(handler);
            // Do not remove next line, handler should be managed to join group lifecycle when server
            // is already in STARTED state
            parent.manage(handler);
            handler.start();

            logger.info("Security domain {} has been been created on path {}", domain.getName(), domain.getContextPath());
        } catch (Exception ex) {
            logger.error("Unable to add a new handler", ex);
        }
    }

    private void removeHandler(Domain domain) {
        Handler handler = getInternalHandler(domain);
        if (handler != null && handler.isStarted()) {
            try {
                logger.info("Stopping handler for {}: {}", domain.getName(), handler);
                handler.stop();
            } catch (Exception ex) {
                logger.error("Unable to stop an handler", ex);
            }

            parent.unmanage(handler);
            parent.removeHandler(handler);
        }
    }

    private Handler getInternalHandler(Domain domain) {
        for (Handler child : parent.getChildHandlers()) {
            if (child instanceof SecurityDomainHandler) {
                SecurityDomainHandler handler = (SecurityDomainHandler) child;

                if (handler.getDomain().equals(domain)) {
                    return handler;
                }
            }
        }

        return null;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        eventManager.subscribeForEvents(this, DomainEvent.class);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }
}
