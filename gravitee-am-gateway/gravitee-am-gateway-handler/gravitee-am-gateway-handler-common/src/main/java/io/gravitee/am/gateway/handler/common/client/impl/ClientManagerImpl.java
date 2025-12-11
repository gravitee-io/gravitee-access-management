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
package io.gravitee.am.gateway.handler.common.client.impl;

import io.gravitee.am.common.event.ApplicationEvent;
import io.gravitee.am.common.event.EventManager;

import io.gravitee.am.gateway.handler.common.client.ClientManager;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientManagerImpl extends AbstractService implements ClientManager, EventListener<ApplicationEvent, Payload>, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(ClientManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private DomainReadinessService domainReadinessService;

    private final ConcurrentMap<String, Client> clients = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Domain> domains = new ConcurrentHashMap<>();

    @Autowired
    private GatewayMetricProvider gatewayMetricProvider;

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Initializing applications for domain {}", domain.getName());
        Flowable<Application> applicationsSource = domain.isMaster() ? applicationRepository.findAll() : applicationRepository.findByDomain(domain.getId());
        applicationsSource
                .map(Application::toClient)
                .filter(Client::isEnabled)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        client -> {
                            gatewayMetricProvider.incrementApp();
                            clients.put(client.getId(), client);
                            logger.info("Application {} loaded for domain {}", client.getClientName(), domain.getName());
                            domainReadinessService.pluginLoaded(domain.getId(), client.getId());
                        },
                        error -> {
                            logger.error("An error has occurred when loading applications for domain {}", domain.getName(), error);
                            domainReadinessService.pluginFailed(domain.getId(), "", error.getMessage());
                        }
                );
    }

    @Override
    public void onEvent(Event<ApplicationEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN &&
                (domain.isMaster() || domain.getId().equals(event.content().getReferenceId()))) {
            // count the event after the test to avoid duplicate events across domains
            gatewayMetricProvider.incrementAppEvt();
            switch (event.type()) {
                case DEPLOY -> {
                    gatewayMetricProvider.incrementApp();
                    deployClient(event.content().getId());
                }
                case UPDATE -> deployClient(event.content().getId());
                case UNDEPLOY -> {
                    removeClient(event.content().getId());
                    gatewayMetricProvider.decrementApp();
                }
                default -> {
                    // No action needed for default case
                }
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for application events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, ApplicationEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for application events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, ApplicationEvent.class, domain.getId());
        if (domain.isMaster()) {
            domains.keySet().forEach(d -> eventManager.unsubscribeForCrossEvents(this, ApplicationEvent.class, d));
        }
    }

    @Override
    public void deploy(Client client) {
        clients.put(client.getId(), client);
    }

    @Override
    public void undeploy(String clientId) {
        clients.remove(clientId);
    }

    @Override
    public Collection<Client> entities() {
        return clients.values();
    }

    @Override
    public Client get(String clientId) {
        return clientId != null ? clients.get(clientId) : null;
    }

    @Override
    public void deployCrossDomain(Domain domain) {
        this.domains.put(domain.getId(), domain);
        this.eventManager.subscribeForEvents(this, ApplicationEvent.class, domain.getId());
    }

    @Override
    public void undeployCrossDomain(Domain domain) {
        this.domains.remove(domain.getId());
        this.eventManager.unsubscribeForCrossEvents(this, ApplicationEvent.class, domain.getId());
    }

    private void deployClient(String applicationId) {
        logger.info("Deploying application {} for domain {}", applicationId, domain.getName());
        applicationRepository.findById(applicationId)
                .map(Application::toClient)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        client -> {
                            if (!client.isEnabled()) {
                                removeClient(applicationId);
                            } else {
                                clients.put(client.getId(), client);
                                logger.info("Application {} loaded for domain {}", applicationId, domain.getName());
                                domainReadinessService.pluginLoaded(domain.getId(), client.getId());
                            }
                        },
                        error -> {
                            logger.error("An error has occurred when loading application {} for domain {}", applicationId, domain.getName(), error);
                            domainReadinessService.pluginFailed(domain.getId(), applicationId, error.getMessage());
                        },
                        () -> logger.error("No application found with id {}", applicationId));
    }

    private void removeClient(String applicationId) {
        logger.info("Removing application {} for domain {}", applicationId, domain.getName());
        Client deletedClient = clients.remove(applicationId);
        if (deletedClient != null) {
            domainReadinessService.pluginUnloaded(domain.getId(), applicationId);
            logger.info("Application {} has been removed for domain {}", applicationId, domain.getName());
        } else {
            logger.info("Application {} was not loaded for domain {}", applicationId, domain.getName());
        }
    }

}
