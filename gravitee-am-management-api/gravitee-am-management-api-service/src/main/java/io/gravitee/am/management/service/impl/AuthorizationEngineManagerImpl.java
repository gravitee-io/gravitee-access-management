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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.authorizationengine.api.AuthorizationEngineProvider;
import io.gravitee.am.common.event.AuthorizationEngineEvent;
import io.gravitee.am.management.service.AuthorizationEngineManager;
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.authorizationengine.core.AuthorizationEnginePluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.service.AuthorizationEngineService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author GraviteeSource Team
 */
@Component
public class AuthorizationEngineManagerImpl extends AbstractService<AuthorizationEngineManager> implements AuthorizationEngineManager, EventListener<AuthorizationEngineEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationEngineManagerImpl.class);

    private final ConcurrentMap<String, AuthorizationEngineProvider> providers = new ConcurrentHashMap<>();

    @Autowired
    private AuthorizationEnginePluginManager authorizationEnginePluginManager;

    @Autowired
    private AuthorizationEngineService authorizationEngineService;

    @Autowired
    private EventManager eventManager;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.debug("Register event listener for authorization engine events for the management API");
        eventManager.subscribeForEvents(this, AuthorizationEngineEvent.class);

        logger.info("Initializing authorization engine manager");
        authorizationEngineService.findAll()
                .flatMapCompletable(this::loadProvider)
                .subscribe(
                        () -> logger.debug("Authorization engine provider loaded successfully"),
                        error -> logger.error("An error has occurred when loading authorization engines", error)
                );
    }

    @Override
    public Maybe<AuthorizationEngineProvider> getProvider(String authorizationEngineId) {
        AuthorizationEngineProvider provider = providers.get(authorizationEngineId);
        return provider != null ? Maybe.just(provider) : Maybe.empty();
    }

    private void removeProvider(String authorizationEngineId) {
        logger.info("Removing authorization engine provider for {}", authorizationEngineId);
        AuthorizationEngineProvider provider = providers.remove(authorizationEngineId);

        if (provider != null) {
            try {
                provider.stop();
            } catch (Exception e) {
                logger.error("An error has occurred while stopping the authorization engine provider: {}", authorizationEngineId, e);
            }
        }
    }

    private Completable loadProvider(AuthorizationEngine authorizationEngine) {
        return Completable.fromAction(() -> {
            logger.info("Loading authorization engine provider: {} [{}]", authorizationEngine.getName(), authorizationEngine.getType());

            try {
                ProviderConfiguration config = new ProviderConfiguration(authorizationEngine.getType(), authorizationEngine.getConfiguration());
                AuthorizationEngineProvider provider = authorizationEnginePluginManager.create(config);

                removeProvider(authorizationEngine.getId());
                if (provider != null) {
                    providers.put(authorizationEngine.getId(), provider);
                    logger.info("Provider for {} successfully loaded", authorizationEngine.getName());
                } else {
                    logger.warn("Provider for {} returned null", authorizationEngine.getName());
                }

            } catch (Exception ex) {
                logger.error("An error occurred while loading authorization engine provider: {} [{}]",
                        authorizationEngine.getName(), authorizationEngine.getType(), ex);
                removeProvider(authorizationEngine.getId());
                throw ex;
            }
        });
    }

    @Override
    public void onEvent(Event<AuthorizationEngineEvent, Payload> event) {
        switch (event.type()) {
            case DEPLOY, UPDATE:
                deployAuthorizationEngine(event.content().getId(), event.type());
                break;
            case UNDEPLOY:
                removeAuthorizationEngine(event.content().getId());
                break;
        }
    }

    private void deployAuthorizationEngine(String authorizationEngineId, AuthorizationEngineEvent eventType) {
        logger.info("Received {} authorization engine event for {}", eventType.name().toLowerCase(), authorizationEngineId);
        authorizationEngineService.findById(authorizationEngineId)
                .flatMapCompletable(this::loadProvider)
                .subscribe(
                        () -> logger.debug("Authorization engine provider for {} successfully deployed/updated", authorizationEngineId),
                        error -> logger.error("Unable to {} authorization engine {}", eventType.name().toLowerCase(), authorizationEngineId, error)
                );
    }

    private void removeAuthorizationEngine(String authorizationEngineId) {
        logger.info("Received an undeploy authorization engine event for {}", authorizationEngineId);
        removeProvider(authorizationEngineId);
    }
}
