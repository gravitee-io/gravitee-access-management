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

import io.gravitee.am.common.event.ApplicationEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ProtectedResourceEvent;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.ProtectedResource;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class ProtectedResourceManagerImpl extends AbstractService implements ProtectedResourceManager, InitializingBean, EventListener<ProtectedResourceEvent, Payload> {

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    private final ConcurrentMap<String, ProtectedResource> resources = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Domain> domains = new ConcurrentHashMap<>();

    @Override
    public void onEvent(Event<ProtectedResourceEvent, Payload> event) {

    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        log.info("Register event listener for application events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, ProtectedResourceEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        log.info("Dispose event listener for application events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, ProtectedResourceEvent.class, domain.getId());
        if (domain.isMaster()) {
            domains.keySet().forEach(d -> eventManager.unsubscribeForCrossEvents(this, ProtectedResourceEvent.class, d));
        }
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
        return List.of();
    }

    @Override
    public ProtectedResource get(String protectedResourceId) {
        return null;
    }

    @Override
    public void deployCrossDomain(Domain domain) {

    }

    @Override
    public void undeployCrossDomain(Domain domain) {

    }
}
