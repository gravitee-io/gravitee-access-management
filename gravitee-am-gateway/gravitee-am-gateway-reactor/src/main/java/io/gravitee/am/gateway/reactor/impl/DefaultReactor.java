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
import io.gravitee.am.gateway.reactor.impl.router.VHostGroupRouter;
import io.gravitee.am.gateway.reactor.impl.transaction.TransactionHandlerFactory;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpHeadersValues;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.service.AbstractService;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.Router;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

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

    // Root router is mutated only under this lock; heavy per-domain work
    // (Spring context refresh, component start) happens before, in parallel.
    private final ReentrantLock routerLock = new ReentrantLock();

    // One VHostGroupRouter per distinct mount path, shared by every domain/vhost mounted on
    // that path, so that the number of routes registered on the root router (and therefore the
    // request-handling recursion depth) no longer grows with the number of domains.
    private final Map<String, VHostGroupRouter> pathGroups = new ConcurrentHashMap<>();

    // Handles removing a domain's members from the VHostGroupRouter(s) it was mounted on, keyed
    // by domain id, so undeploy actually forgets the domain instead of only clearing its router.
    private final Map<String, List<Runnable>> domainUnmountActions = new ConcurrentHashMap<>();

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
        routerLock.lock();
        try {
            Domain domain = domainHandler.getDomain();
            List<Runnable> unmountActions = new ArrayList<>();

            if (domain.isVhostMode()) {
                // Mount the same router for each virtual host / path.
                // Sort vhosts to ensure proper routing order:
                // - More specific paths (longer) are checked first
                // - "/" (catch-all) is always checked last
                List<VirtualHost> sortedVhosts = domain.getVhosts().stream()
                        .sorted(Comparator.comparing((VirtualHost vhost) -> vhost.getPath().equals("/") ? 1 : 0)
                                .thenComparing(Comparator.comparing(VirtualHost::getPath).reversed()))
                        .toList();

                sortedVhosts.forEach(virtualHost -> {
                    VHostGroupRouter group = groupFor(sanitizePath(virtualHost.getPath()));
                    Object member = group.addMember(vertx, domain, virtualHost, domainHandler.router());
                    unmountActions.add(() -> group.removeMember(member));
                });
            } else {
                VHostGroupRouter group = groupFor(sanitizePath(domain.getPath()));
                Object member = group.addMember(vertx, domain, domainHandler.router());
                unmountActions.add(() -> group.removeMember(member));
            }

            domainUnmountActions.put(domain.getId(), unmountActions);
        } finally {
            routerLock.unlock();
        }
    }

    /**
     * Returns the {@link VHostGroupRouter} handling the given (already sanitized) mount path,
     * creating and mounting it on the root router the first time it is requested. Every
     * domain/vhost sharing this path is later registered on the very same group, instead of
     * each getting its own entry on the root router.
     */
    private VHostGroupRouter groupFor(String path) {
        return pathGroups.computeIfAbsent(path, p -> {
            VHostGroupRouter group = VHostGroupRouter.create(vertx);
            this.router.route(p).subRouter(group.asRxRouter());
            return group;
        });
    }

    private String sanitizePath(String path) {
        // Vert.x 5 requires sub router mount paths to end with /*
        if(path.endsWith("/")) {
            return path + "*";
        }

        return path + "/*";
    }

    @Override
    public void unMountDomain(VertxSecurityDomainHandler domainHandler) {
        routerLock.lock();
        try {
            Domain domain = domainHandler.getDomain();

            List<Runnable> unmountActions = domainUnmountActions.remove(domain.getId());
            if (unmountActions != null) {
                unmountActions.forEach(Runnable::run);
            }

            domainHandler.router().clear();
        } finally {
            routerLock.unlock();
        }
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
