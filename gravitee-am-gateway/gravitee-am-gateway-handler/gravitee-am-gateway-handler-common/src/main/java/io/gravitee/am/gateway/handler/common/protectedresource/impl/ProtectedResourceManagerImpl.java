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
package io.gravitee.am.gateway.handler.common.protectedresource.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ProtectedResourceEvent;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class ProtectedResourceManagerImpl extends AbstractService implements ProtectedResourceManager, InitializingBean, EventListener<ProtectedResourceEvent, Payload> {

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private ProtectedResourceRepository protectedResourceRepository;

    @Autowired
    private GatewayMetricProvider gatewayMetricProvider;

    private final ConcurrentMap<String, ProtectedResource> resources = new ConcurrentHashMap<>();


    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Initializing protected resources for domain {}", domain.getName());
        Flowable<ProtectedResource> protectedResourceFlowable = domain.isMaster() ? protectedResourceRepository.findAll() : protectedResourceRepository.findByDomain(domain.getId());
        protectedResourceFlowable
                .subscribeOn(Schedulers.io())
                .subscribe(
                        res -> {
                            gatewayMetricProvider.incrementProtectedResource();
                            resources.put(res.getId(), res);
                            log.info("Protected Resource {} loaded for domain {}", res.getName(), domain.getName());
                        },
                        error -> log.error("An error has occurred when loading protected resources for domain {}", domain.getName(), error)
                );
    }

    @Override
    public void onEvent(Event<ProtectedResourceEvent, Payload> event) {
        log.debug("Receive protected resource event {} for id {}", event.type(), event.content().getId());
        if (event.content().getReferenceType() == ReferenceType.DOMAIN &&
                (domain.isMaster() || domain.getId().equals(event.content().getReferenceId()))) {
            // count the event after the test to avoid duplicate events across domains
            gatewayMetricProvider.incrementProtectedResourceEvt();
            switch (event.type()) {
                case DEPLOY -> {
                    gatewayMetricProvider.incrementProtectedResource();
                    deployProtectedResource(event.content().getId());
                }
                case UPDATE -> deployProtectedResource(event.content().getId());
                case UNDEPLOY -> {
                    removeProtectedResource(event.content().getId());
                    gatewayMetricProvider.decrementProtectedResource();
                }
                default -> {
                    log.warn("Unsupported protected resource event {}", event.type());
                }
            }
        }
    }

    private void removeProtectedResource(String protectedResourceId) {
        log.info("Removing protected resource {} for domain {}", protectedResourceId, domain.getName());
        ProtectedResource deletedProtectedResource = resources.remove(protectedResourceId);
        if (deletedProtectedResource != null) {
            log.info("Protected Resource {} has been removed for domain {}", protectedResourceId, domain.getName());
        } else {
            log.info("Protected Resource {} was not loaded for domain {}", protectedResourceId, domain.getName());
        }
    }

    private void deployProtectedResource(String protectedResourceId) {
        log.info("Deploying protected resource {} for domain {}", protectedResourceId, domain.getName());
        protectedResourceRepository.findById(protectedResourceId)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        res -> {
                            resources.put(res.getId(), res);
                            log.info("Protected Resource {} loaded for domain {}", protectedResourceId, domain.getName());

                        },
                        error -> log.error("An error has occurred when loading protected resource {} for domain {}", protectedResourceId, domain.getName(), error),
                        () -> log.error("No protected resource found with id {}", protectedResourceId));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        log.info("Register event listener for protected resource events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, ProtectedResourceEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        log.info("Dispose event listener for protected resource events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, ProtectedResourceEvent.class, domain.getId());
    }

    @Override
    public void deploy(ProtectedResource resource) {
        resources.put(resource.getId(), resource);
    }

    @Override
    public void undeploy(String protectedResourceId) {
        resources.remove(protectedResourceId);
    }

    @Override
    public Collection<ProtectedResource> entities() {
        return resources.values();
    }

    @Override
    public ProtectedResource get(String protectedResourceId) {
        return protectedResourceId != null ? resources.get(protectedResourceId) : null;
    }
}
