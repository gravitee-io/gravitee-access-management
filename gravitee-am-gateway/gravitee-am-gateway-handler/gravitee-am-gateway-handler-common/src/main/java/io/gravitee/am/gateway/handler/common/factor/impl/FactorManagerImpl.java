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
package io.gravitee.am.gateway.handler.common.factor.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.FactorEvent;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.plugins.factor.core.FactorPluginManager;
import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import io.gravitee.am.service.FactorService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FactorManagerImpl extends AbstractService implements FactorManager, InitializingBean, EventListener<FactorEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(FactorManagerImpl.class);
    private final ConcurrentMap<String, FactorProvider> factorProviders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Factor> factors = new ConcurrentHashMap<>();

    @Autowired
    private FactorService factorService;

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private FactorPluginManager factorPluginManager;

    @Autowired
    private io.gravitee.am.monitoring.DomainReadinessService domainReadinessService;

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing factors for domain {}", domain.getName());
        factorService.findByDomain(domain.getId())
                .subscribe(
                        this::updateFactor,
                        error -> logger.error("Unable to initialize factors for domain {}", domain.getName(), error));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for factor events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, FactorEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for factor events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, FactorEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<FactorEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateFactor(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeFactor(event.content().getId());
                    break;
            }
        }
    }

    @Override
    public FactorProvider get(String factorId) {
        return factorProviders.get(factorId);
    }

    @Override
    public Factor getFactor(String factorId) {
        return factors.get(factorId);
    }

    @Override
    public void updateFactor(String factorId) {
        updateFactor(factorId, FactorEvent.UPDATE);
    }

    private void updateFactor(String factorId, FactorEvent factorEvent) {
        final String eventType = factorEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} factor event for {}", domain.getName(), eventType, factorId);
        factorService.findById(factorId)
                .subscribe(
                        this::updateFactor,
                        error -> logger.error("Unable to load factor for domain {}", domain.getName(), error),
                        () -> logger.error("No factor found with id {}", factorId));
    }

    private void removeFactor(String factorId) {
        logger.info("Domain {} has received form event, remove factor {}", domain.getName(), factorId);
        factorProviders.remove(factorId);
        factors.remove(factorId);
    }

    private void updateFactor(Factor factor) {
        try {
            if (needDeployment(factor)) {
                var factorProviderConfig = new ProviderConfiguration(factor.getType(), factor.getConfiguration());
                var factorProvider = factorPluginManager.create(factorProviderConfig);
                this.factorProviders.put(factor.getId(), factorProvider);
                this.factors.put(factor.getId(), factor);
                logger.info("Factor {} loaded for domain {}", factor.getName(), domain.getName());
                domainReadinessService.updatePluginStatus(domain.getId(), factor.getId(), factor.getName(), true, null);
            } else {
                logger.info("Factor {} already loaded for domain {}", factor.getName(), domain.getName());
                domainReadinessService.updatePluginStatus(domain.getId(), factor.getId(), factor.getName(), true, null);
            }
        } catch (Exception ex) {
            this.factorProviders.remove(factor.getId());
            logger.error("Unable to create factor provider for domain {}", domain.getName(), ex);
            domainReadinessService.updatePluginStatus(domain.getId(), factor.getId(), factor.getName(), false, ex.getMessage());
        }
    }

    /**
     * @param factor
     * @return true if the Factor has never been deployed or if the deployed version is not up to date
     */
    private boolean needDeployment(Factor factor) {
        final Factor deployedFactor = this.factors.get(factor.getId());
        return (deployedFactor == null || deployedFactor.getUpdatedAt().before(factor.getUpdatedAt()));
    }

    @Override
    public Optional<Factor> getClientFactor(Client client, String factorId) {
        if (client == null || client.getFactorSettings() == null || CollectionUtils.isEmpty(client.getFactorSettings().getApplicationFactors())) {
            return Optional.empty();
        }

        return client.getFactorSettings()
                .getApplicationFactors()
                .stream()
                .map(ApplicationFactorSettings::getId)
                .filter(id -> id.equals(factorId))
                .map(this::getFactor)
                .findFirst();
    }

}
