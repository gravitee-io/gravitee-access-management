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
package io.gravitee.am.gateway.handler.common.authorizationengine.impl;

import io.gravitee.am.authorizationengine.api.AuthorizationEngineProvider;
import io.gravitee.am.common.event.AuthorizationEngineEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.handler.common.authorizationengine.AuthorizationEngineManager;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.plugins.authorizationengine.core.AuthorizationEnginePluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.repository.management.api.AuthorizationEngineRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author GraviteeSource Team
 */
public class AuthorizationEngineManagerImpl extends AbstractService implements AuthorizationEngineManager, InitializingBean, EventListener<AuthorizationEngineEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationEngineManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private AuthorizationEnginePluginManager authorizationEnginePluginManager;

    @Autowired
    private AuthorizationEngineRepository authorizationEngineRepository;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private DomainReadinessService domainReadinessService;

    private final ConcurrentMap<String, AuthorizationEngineProvider> providers = new ConcurrentHashMap<>();

    @Override
    public Maybe<AuthorizationEngineProvider> get(String id) {
        AuthorizationEngineProvider provider = providers.get(id);
        return (provider != null) ? Maybe.just(provider) : Maybe.empty();
    }

    @Override
    public Maybe<AuthorizationEngineProvider> getDefault() {
        final int size = providers.size();
        if (size == 0) {
            return Maybe.empty();
        }
        if (size > 1) {
            return Maybe.error(new IllegalStateException("Multiple authorization engine providers found. Only one provider is allowed per domain."));
        }
        return Maybe.just(providers.values().iterator().next());
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing authorization engines for domain {}", domain.getName());

        authorizationEngineRepository.findByDomain(domain.getId())
                .flatMapSingle(authorizationEngine ->
                        loadAuthorizationEngine(authorizationEngine)
                                .doOnSuccess(ae ->
                                        logger.info("Authorization engine {} loaded for domain {}", ae.getName(), domain.getName()))
                                .doOnError(error ->
                                        logger.warn("Failed to load authorization engine {} for domain {}",
                                                authorizationEngine.getName(), domain.getName(), error))
                )
                .ignoreElements()
                .doOnComplete(() ->
                        logger.info("All authorization engines initialized for domain {}", domain.getName()))
                .subscribe(
                        () -> {}, // complete handled above
                        error -> {
                            logger.error("Unexpected error while initializing authorization engines for domain {}", domain.getName(), error);
                            domainReadinessService.pluginInitFailed(domain.getId(), Type.AUTHORIZATION_ENGINE.name(), error.getMessage());
                        }
                );
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for authorization engine events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, AuthorizationEngineEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for authorization engine events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, AuthorizationEngineEvent.class, domain.getId());
        clearProviders();
    }

    @Override
    public void onEvent(Event<AuthorizationEngineEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY, UPDATE -> updateAuthorizationEngine(event.content().getId(), event.type());
                case UNDEPLOY -> removeAuthorizationEngine(event.content().getId());
                default -> {
                    // No action needed for default case
                }
            }
        }
    }

    private Single<AuthorizationEngine> loadAuthorizationEngine(AuthorizationEngine authorizationEngine) {
        logger.info("Loading authorization engine: {} [{}]", authorizationEngine.getName(), authorizationEngine.getType());
        return deployProvider(authorizationEngine);
    }

    private void updateAuthorizationEngine(String authorizationEngineId, AuthorizationEngineEvent authorizationEngineEvent) {
        final String eventType = authorizationEngineEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} authorization engine event for {}",
                domain.getName(), eventType, authorizationEngineId);
        authorizationEngineRepository.findById(authorizationEngineId)
                .flatMapSingle(authorizationEngine ->
                        loadAuthorizationEngine(authorizationEngine)
                                .doOnSuccess(ae -> logger.info("Authorization engine {} {} for domain {}", authorizationEngineId, eventType, domain.getName()))
                                .doOnError(error -> logger.error("Unable to {} authorization engine for domain {}", eventType, domain.getName(), error)))
                .switchIfEmpty(Maybe.fromRunnable(() -> logger.error("No authorization engine found with id {}", authorizationEngineId)))
                .onErrorComplete(error -> {
                    logger.error("An error has occurred when {} authorization engine {} for domain {}",
                            eventType, authorizationEngineId, domain.getName(), error);
                    return true;
                })
                .ignoreElement()
                .subscribe();
    }

    private void removeAuthorizationEngine(String authorizationEngineId) {
        logger.info("Domain {} has received authorization engine event, delete authorization engine {}", domain.getName(), authorizationEngineId);
        clearProvider(authorizationEngineId);
    }

    private Single<AuthorizationEngine> deployProvider(AuthorizationEngine authorizationEngine) {
        try {
            ProviderConfiguration config = new ProviderConfiguration(authorizationEngine.getType(), authorizationEngine.getConfiguration());

            domainReadinessService.initPluginSync(domain.getId(), authorizationEngine.getId(), Type.AUTHORIZATION_ENGINE.name());
            AuthorizationEngineProvider provider = authorizationEnginePluginManager.create(config);
            clearProvider(authorizationEngine.getId());
            if (provider != null) {
                providers.put(authorizationEngine.getId(), provider);

                logger.info("Authorization engine {} deployed for domain {}", authorizationEngine.getId(), domain.getName());
                domainReadinessService.pluginLoaded(domain.getId(), authorizationEngine.getId());
                return Single.just(authorizationEngine);
            } else {
                String errorMsg = "Failed to create authorization engine provider";
                domainReadinessService.pluginFailed(domain.getId(), authorizationEngine.getId(), errorMsg);
                return Single.error(new IllegalStateException(errorMsg));
            }
        } catch (Exception ex) {
            logger.error("An error has occurred while loading authorization engine: {} [{}]",
                    authorizationEngine.getName(), authorizationEngine.getType(), ex);
            clearProvider(authorizationEngine.getId());
            domainReadinessService.pluginFailed(domain.getId(), authorizationEngine.getId(), ex.getMessage());
            return Single.error(ex);
        }
    }

    private void clearProviders() {
        providers.keySet().forEach(this::clearProvider);
    }

    private void clearProvider(String authorizationEngineId) {
        AuthorizationEngineProvider provider = providers.remove(authorizationEngineId);

        if (provider != null) {
            try {
                domainReadinessService.pluginUnloaded(domain.getId(), authorizationEngineId);
                logger.info("Stopping authorization engine provider: {}", authorizationEngineId);
                provider.stop();
            } catch (Exception e) {
                logger.error("An error has occurred while stopping the authorization engine provider: {}", authorizationEngineId, e);
            }
        }
    }
}
