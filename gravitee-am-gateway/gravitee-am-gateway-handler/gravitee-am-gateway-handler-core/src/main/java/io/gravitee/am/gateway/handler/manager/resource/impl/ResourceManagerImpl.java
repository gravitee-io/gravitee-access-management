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
package io.gravitee.am.gateway.handler.manager.resource.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ResourceEvent;
import io.gravitee.am.gateway.handler.manager.factor.FactorManager;
import io.gravitee.am.gateway.handler.manager.factor.impl.FactorManagerImpl;
import io.gravitee.am.gateway.handler.manager.resource.ResourceManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.resource.core.ResourcePluginManager;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.ServiceResourceService;
import io.gravitee.am.service.exception.ResourceNotFoundException;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Maybe;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourceManagerImpl extends AbstractService implements ResourceManager, EventListener<ResourceEvent, Payload>, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(FactorManagerImpl.class);

    @Autowired
    private ResourcePluginManager resourcePluginManager;

    @Autowired
    private FactorManager factorManager;

    @Autowired
    private FactorService factorService;

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private ServiceResourceService resourceService;

    private Map<String, ResourceProvider> resourceProviders = new ConcurrentHashMap<>();

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for resource events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, ResourceEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for resource events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, ResourceEvent.class, domain.getId());
        // stop providers and remove them from the local cache
        Set<String> resourceIds = new HashSet(this.resourceProviders.keySet());
        resourceIds.stream().forEach(this::unloadResource);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // load all known resource for the domain on startup
        resourceService.findByDomain(this.domain.getId())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        res -> {
                            ResourceProvider provider = resourcePluginManager.create(res.getType(), res.getConfiguration());
                            provider.start();
                            resourceProviders.put(res.getId(), provider);
                            logger.info("Resource {} loaded for domain {}", res.getName(), domain.getName());
                        },
                        error -> logger.error("Unable to initialize resources for domain {}", domain.getName(), error)
                );
    }

    public ResourceProvider getResourceProvider(String resourceId) {
        return this.resourceProviders.get(resourceId);
    }

    @Override
    public void onEvent(Event<ResourceEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                    loadResource(event.content().getId());
                    break;
                case UPDATE:
                    refreshResource(event.content().getId());
                    break;
                case UNDEPLOY:
                    unloadResource(event.content().getId());
                    break;
            }
        }
    }

    private void loadResource(String resourceId) {
        resourceService.findById(resourceId)
                .switchIfEmpty(Maybe.error(new ResourceNotFoundException("Resource " + resourceId + " not found")))
                .map(res -> resourcePluginManager.create(res.getType(), res.getConfiguration()))
                .subscribe(
                        provider -> {
                            provider.start();
                            this.resourceProviders.put(resourceId, provider);
                        },
                        error -> logger.error("Initialization of Resource provider '{}' failed", error));
    }

    private void refreshResource(String resourceId) {
        unloadResource(resourceId);
        loadResource(resourceId);
    }

    private void unloadResource(String resourceId) {
        try {
            ResourceProvider resourceProvider = getResourceProvider(resourceId);
            if (resourceProvider != null) {
                resourceProvider.stop();
                this.resourceProviders.remove(resourceId);
            }
        } catch (Exception e) {
            logger.error("Resource '{}' stopped with error", e);
        }
    }
}
