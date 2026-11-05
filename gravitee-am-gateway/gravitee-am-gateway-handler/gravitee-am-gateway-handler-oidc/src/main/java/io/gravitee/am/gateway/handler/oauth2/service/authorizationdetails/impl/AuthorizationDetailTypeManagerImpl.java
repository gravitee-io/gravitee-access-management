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
package io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.impl;

import io.gravitee.am.common.event.AuthorizationDetailTypeEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailTypeManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.repository.management.api.AuthorizationDetailTypeRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.CustomLog;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@CustomLog
public class AuthorizationDetailTypeManagerImpl extends AbstractService implements AuthorizationDetailTypeManager, InitializingBean,
        EventListener<AuthorizationDetailTypeEvent, Payload> {

    private static final String PRELOAD_PLUGIN_ID = Type.AUTHORIZATION_DETAIL_TYPE.name();

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private AuthorizationDetailTypeRepository authorizationDetailTypeRepository;

    @Autowired
    private DomainReadinessService domainReadinessService;

    private final ConcurrentMap<String, AuthorizationDetailType> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AuthorizationDetailType> byType = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() {
        log.info("Initializing authorization detail types for domain {}", domain.getName());
        domainReadinessService.initPluginSync(domain.getId(), PRELOAD_PLUGIN_ID, Type.AUTHORIZATION_DETAIL_TYPE.name());
        authorizationDetailTypeRepository.findByDomain(domain.getId())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        authorizationDetailType -> {
                            domainReadinessService.initPluginSync(domain.getId(), authorizationDetailType.getId(), Type.AUTHORIZATION_DETAIL_TYPE.name());
                            index(authorizationDetailType);
                            domainReadinessService.pluginLoaded(domain.getId(), authorizationDetailType.getId());
                        },
                        error -> {
                            log.error("An error has occurred when loading authorization detail types for domain {}", domain.getName(), error);
                            domainReadinessService.pluginInitFailed(domain.getId(), Type.AUTHORIZATION_DETAIL_TYPE.name(), error.getMessage());
                        },
                        () -> {
                            log.info("Authorization detail types loaded for domain {}", domain.getName());
                            domainReadinessService.pluginLoaded(domain.getId(), PRELOAD_PLUGIN_ID);
                        });

        log.info("Register event listener for authorization detail type events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, AuthorizationDetailTypeEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        log.info("Dispose event listener for authorization detail type events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, AuthorizationDetailTypeEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<AuthorizationDetailTypeEvent, Payload> event) {
        log.debug("Receive authorization detail type event {} for id {}", event.type(), event.content().getId());
        if (event.content().getReferenceType() != ReferenceType.DOMAIN
                || !domain.getId().equals(event.content().getReferenceId())) {
            return;
        }
        final String id = event.content().getId();
        switch (event.type()) {
            case DEPLOY, UPDATE -> reload(id);
            case UNDEPLOY -> unload(id);
        }
    }

    @Override
    public Optional<AuthorizationDetailType> findByType(String type) {
        return type == null ? Optional.empty() : Optional.ofNullable(byType.get(type));
    }

    @Override
    public List<String> findDiscoveryTypes() {
        return byType.values().stream()
                .filter(AuthorizationDetailType::isDiscovery)
                .map(AuthorizationDetailType::getType)
                .sorted()
                .toList();
    }

    private void reload(String id) {
        log.info("Loading authorization detail type {} for domain {}", id, domain.getName());
        domainReadinessService.initPluginSync(domain.getId(), id, Type.AUTHORIZATION_DETAIL_TYPE.name());
        authorizationDetailTypeRepository.findById(id)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        authorizationDetailType -> {
                            index(authorizationDetailType);
                            log.info("Authorization detail type {} loaded for domain {}", id, domain.getName());
                            domainReadinessService.pluginLoaded(domain.getId(), id);
                        },
                        error -> {
                            log.error("An error has occurred when loading authorization detail type {} for domain {}", id, domain.getName(), error);
                            domainReadinessService.pluginFailed(domain.getId(), id, error.getMessage());
                        },
                        () -> {
                            log.warn("No authorization detail type found with id {} for domain {}", id, domain.getName());
                            unload(id);
                        });
    }

    private void unload(String id) {
        AuthorizationDetailType removed = byId.remove(id);
        if (removed != null) {
            byType.remove(removed.getType(), removed);
            log.info("Authorization detail type {} has been removed for domain {}", id, domain.getName());
        }
        domainReadinessService.pluginRemoved(domain.getId(), id);
    }

    private void index(AuthorizationDetailType authorizationDetailType) {
        AuthorizationDetailType previous = byId.put(authorizationDetailType.getId(), authorizationDetailType);
        if (previous != null) {
            byType.remove(previous.getType(), previous);
        }
        byType.put(authorizationDetailType.getType(), authorizationDetailType);
    }
}
