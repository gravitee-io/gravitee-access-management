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
package io.gravitee.am.gateway.handler.oauth2.service.scope.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ScopeEvent;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ScopeManagerImpl extends AbstractService implements ScopeManager, InitializingBean, EventListener<ScopeEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(ScopeManagerImpl.class);
    private ConcurrentMap<String, Scope> scopes = new ConcurrentHashMap<>();

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private DomainReadinessService domainReadinessService;

    @Deprecated
    @Value("${legacy.openid.always_enhance_scopes:false}")
    private boolean alwaysProvideEnhancedScopes;

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing scopes for domain {}", domain.getName());
        scopeService.findByDomain(domain.getId(), 0, Integer.MAX_VALUE)
                .subscribe(
                        s -> {
                            updateScopes(s);
                            logger.info("Scopes loaded for domain {}", domain.getName());
                        },
                        error -> logger.error("Unable to initialize scopes for domain {}", domain.getName(), error));

        logger.info("Register event listener for scopes events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, ScopeEvent.class, domain.getId());

    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for scopes events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, ScopeEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<ScopeEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateScope(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeScope(event.content().getId());
                    break;
            }
        }
    }

    @Override
    public Set<Scope> findAll() {
        return new HashSet<>(scopes.values());
    }

    @Override
    public Scope findByKey(String scopeKey) {
        return scopes.get(scopeKey);
    }

    private void updateScopes(Set<Scope> scopes) {
        scopes
                .stream()
                .forEach(scope -> {
                    this.scopes.put(scope.getKey(), scope);
                    logger.info("Scope {} loaded for domain {}", scope.getKey(), domain.getName());
                });
    }

    private void updateScopes(Page<Scope> scopes) {
        scopes.getData()
                .stream()
                .forEach(scope -> {
                    this.scopes.put(scope.getKey(), scope);
                    logger.info("Scope {} loaded for domain {}", scope.getKey(), domain.getName());
                });
    }

    private void updateScope(String scopeId, ScopeEvent scopeEvent) {
        final String eventType = scopeEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} scope event for {}", domain.getName(), eventType, scopeId);
        domainReadinessService.initPluginSync(domain.getId(), scopeId, Type.SCOPE.name());
        scopeService.findById(scopeId)
                .subscribe(
                        scope -> {
                            updateScopes(Collections.singleton(scope));
                            logger.info("Scope {} {}d for domain {}", scopeId, eventType, domain.getName());
                            domainReadinessService.pluginLoaded(domain.getId(), scopeId);
                        },
                        error -> {
                            logger.error("Unable to {} scope for domain {}", eventType, domain.getName(), error);
                            domainReadinessService.pluginFailed(domain.getId(), scopeId, error.getMessage());
                        },
                        () -> {
                            logger.error("No scope found with id {}", scopeId);
                            domainReadinessService.pluginUnloaded(domain.getId(), scopeId);
                        });
    }

    private void removeScope(String scopeId) {
        logger.info("Domain {} has received scope event, delete scope {}", domain.getName(), scopeId);
        scopes.values().removeIf(scope -> scopeId.equals(scope.getId()));
        domainReadinessService.pluginUnloaded(domain.getId(), scopeId);
    }

    public boolean isParameterizedScope(String scopeKey) {
        return Optional.ofNullable(this.scopes.get(scopeKey)).map(Scope::isParameterized).orElse(false);
    }

    @Override
    @Deprecated
    public boolean alwaysProvideEnhancedScopes() {
        return this.alwaysProvideEnhancedScopes;
    }
}
