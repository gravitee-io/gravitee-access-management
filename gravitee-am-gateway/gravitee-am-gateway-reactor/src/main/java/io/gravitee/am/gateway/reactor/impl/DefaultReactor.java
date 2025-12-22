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

import io.gravitee.am.common.event.DomainEvent;
import io.gravitee.am.gateway.handler.vertx.VertxSecurityDomainHandler;
import io.gravitee.am.gateway.reactor.Reactor;
import io.gravitee.am.gateway.reactor.SecurityDomainHandlerRegistry;
import io.gravitee.am.gateway.reactor.impl.router.VHostRouter;
import io.gravitee.am.gateway.reactor.impl.transaction.TransactionHandlerFactory;
import io.gravitee.am.model.Domain;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpHeadersValues;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.service.AbstractService;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.Router;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultReactor extends AbstractService implements Reactor, EventListener<DomainEvent, Domain>, InitializingBean {

    @Autowired
    private Environment environment;

    @Autowired
    private SecurityDomainHandlerRegistry securityDomainHandlerRegistry;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private Vertx vertx;

    private Router router;

    @Autowired
    private TransactionHandlerFactory transactionHandlerFactory;

    @Autowired
    private GatewayMetricProvider gatewayMetricProvider;

    @Override
    public void doStart() throws Exception {
        super.doStart();

        eventManager.subscribeForEvents(this, DomainEvent.class);
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();

        securityDomainHandlerRegistry.clear();
    }

    public boolean isStarted() {
        return lifecycle.started();
    }

    @Override
    public void onEvent(Event<DomainEvent, Domain> event) {
        gatewayMetricProvider.incrementDomainEvt();
        switch (event.type()) {
            case DEPLOY:
                gatewayMetricProvider.incrementDomain();
                securityDomainHandlerRegistry.create(event.content());
                break;
            case UPDATE:
                securityDomainHandlerRegistry.update(event.content());
                break;
            case UNDEPLOY:
                securityDomainHandlerRegistry.remove(event.content());
                gatewayMetricProvider.decrementDomain();
                break;
        }
    }

    @Override
    public Router route() {
        return router;
    }

    @Override
    public void mountDomain(VertxSecurityDomainHandler domainHandler) {

        Domain domain = domainHandler.getDomain();

        if (domain.isVhostMode()) {
            // Mount the same router for each virtual host / path.
            domain.getVhosts().forEach(virtualHost -> this.router.mountSubRouter(sanitizePath(virtualHost.getPath()), VHostRouter.router(domain, virtualHost, domainHandler.router())));
        } else {
            this.router.mountSubRouter(sanitizePath(domain.getPath()), VHostRouter.router(domain, domainHandler.router()));
        }
    }

    private String sanitizePath(String path) {

        if(path.endsWith("/")) {
            return path;
        }

        return path + "/";
    }

    @Override
    public void unMountDomain(VertxSecurityDomainHandler domainHandler) {

        domainHandler.router().clear();
    }

    @Override
    public void afterPropertiesSet() {
        router = Router.router(vertx);
        router.route().handler(transactionHandlerFactory.create());
        router.route().last().handler(context -> sendNotFound(context.response()));
    }

    private void sendNotFound(HttpServerResponse serverResponse) {
        // Send a NOT_FOUND HTTP status code (404)
        serverResponse.setStatusCode(HttpStatusCode.NOT_FOUND_404);

        String message = environment.getProperty("http.errors[404].message", "No security domain matches the request URI.");
        serverResponse.headers().set(HttpHeaders.CONTENT_LENGTH, Integer.toString(message.length()));
        serverResponse.headers().set(HttpHeaders.CONTENT_TYPE, "text/plain");
        serverResponse.headers().set(HttpHeaders.CONNECTION, HttpHeadersValues.CONNECTION_CLOSE);
        serverResponse.write(Buffer.buffer(message));

        serverResponse.end();
    }

}
