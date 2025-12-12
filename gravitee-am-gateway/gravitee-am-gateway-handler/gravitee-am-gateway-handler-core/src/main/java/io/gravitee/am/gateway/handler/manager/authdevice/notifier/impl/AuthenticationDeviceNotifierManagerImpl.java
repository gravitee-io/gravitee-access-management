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
package io.gravitee.am.gateway.handler.manager.authdevice.notifier.impl;

import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierProvider;
import io.gravitee.am.common.event.AuthenticationDeviceNotifierEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.plugins.authdevice.notifier.core.AuthenticationDeviceNotifierPluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.service.AuthenticationDeviceNotifierService;
import io.gravitee.am.service.exception.AuthenticationDeviceNotifierNotFoundException;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationDeviceNotifierManagerImpl extends AbstractService implements AuthenticationDeviceNotifierManager, EventListener<AuthenticationDeviceNotifierEvent, Payload>, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationDeviceNotifierManagerImpl.class);

    @Autowired
    private AuthenticationDeviceNotifierPluginManager deviceNotifierPluginManager;

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private AuthenticationDeviceNotifierService deviceNotifierService;

    @Autowired
    private DomainReadinessService domainReadinessService;

    private final Map<String, AuthenticationDeviceNotifierProvider> deviceNotifierProviders = new ConcurrentHashMap<>();
    private final Map<String, AuthenticationDeviceNotifier> deviceNotifiers = new ConcurrentHashMap<>();

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for authentication device notifier events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, AuthenticationDeviceNotifierEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for authentication device notifier events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, AuthenticationDeviceNotifierEvent.class, domain.getId());
        // stop providers and remove them from the local cache
        Set<String> providerIds = new HashSet<>(this.deviceNotifierProviders.keySet());
        providerIds.forEach(this::unloadDeviceNotifierProvider);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // load all known resource for the domain on startup
        deviceNotifierService.findByDomain(this.domain.getId())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        notifier -> {
                            domainReadinessService.initPluginSync(domain.getId(), notifier.getId(), Type.AUTH_DEVICE_NOTIFIER.name());
                            var providerConfiguration = new ProviderConfiguration(notifier.getType(), notifier.getConfiguration());
                            var provider = deviceNotifierPluginManager.create(providerConfiguration);
                            provider.start();
                            deviceNotifiers.put(notifier.getId(), notifier);
                            deviceNotifierProviders.put(notifier.getId(), provider);
                            logger.info("Authentication Device Notifier {} loaded for domain {}", notifier.getName(), domain.getName());
                            domainReadinessService.pluginLoaded(domain.getId(), notifier.getId());
                        },
                        error -> logger.error("Unable to initialize Authentication Device Notifiers for domain {}", domain.getName(), error)
                );
    }

    @Override
    public AuthenticationDeviceNotifierProvider getAuthDeviceNotifierProvider(String notifierId) {
        return this.deviceNotifierProviders.get(notifierId);
    }

    @Override
    public Collection<AuthenticationDeviceNotifierProvider> getAuthDeviceNotifierProviders() {
        return this.deviceNotifierProviders.values();
    }

    @Override
    public void onEvent(Event<AuthenticationDeviceNotifierEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    reloadDeviceNotifierProvider(event.content().getId());
                    break;
                case UNDEPLOY:
                    unloadDeviceNotifierProvider(event.content().getId());
                    break;
            }
        }
    }

    private void reloadDeviceNotifierProvider(String notifierId) {
        deviceNotifierService.findById(notifierId)
                .switchIfEmpty(Maybe.error(() -> new AuthenticationDeviceNotifierNotFoundException("Authentication Device Notifier " + notifierId + " not found")))
                .map(notifier -> {
                    if (needDeployment(notifier)) {
                        domainReadinessService.initPluginSync(domain.getId(), notifier.getId(), Type.AUTH_DEVICE_NOTIFIER.name());
                        unloadDeviceNotifierProvider(notifierId);
                        var provider = deviceNotifierPluginManager.create(new ProviderConfiguration(notifier.getType(), notifier.getConfiguration()));
                        provider.start();
                        this.deviceNotifierProviders.put(notifier.getId(), provider);
                        this.deviceNotifiers.put(notifier.getId(), notifier);
                        domainReadinessService.pluginLoaded(domain.getId(), notifier.getId());
                    }
                    return notifier;
                })
                .subscribe(
                        provider -> logger.debug("Initialization of Authentication Device Notifier provider '{}' successful", notifierId),
                        error -> {
                            logger.error("Initialization of Authentication Device Notifier provider '{}' failed", notifierId, error);
                            domainReadinessService.pluginFailed(domain.getId(), notifierId, error.getMessage());
                        },
                        ()-> logger.debug("Initialization of Authentication Device Notifier provider '{}' already done", notifierId)
                );
    }

    private void unloadDeviceNotifierProvider(String notifierId) {
        try {
            AuthenticationDeviceNotifierProvider provider = getAuthDeviceNotifierProvider(notifierId);
            if (provider != null) {
                provider.stop();
                this.deviceNotifierProviders.remove(notifierId);
                this.deviceNotifiers.remove(notifierId);
                domainReadinessService.pluginUnloaded(domain.getId(), notifierId);
            }
        } catch (Exception e) {
            logger.error("Authentication Device Notifier '{}' stopped with error", notifierId, e);
        }
    }

    /**
     * @param notifier
     * @return true if the Notifier has never been deployed or if the deployed version is not up to date
     */
    private boolean needDeployment(AuthenticationDeviceNotifier notifier) {
        final AuthenticationDeviceNotifier deployedNotifier = this.deviceNotifiers.get(notifier.getId());
        return (deployedNotifier == null || deployedNotifier.getUpdatedAt().before(notifier.getUpdatedAt()));
    }
}
