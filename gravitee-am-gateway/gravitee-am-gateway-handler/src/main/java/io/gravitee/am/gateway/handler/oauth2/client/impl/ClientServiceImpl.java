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
package io.gravitee.am.gateway.handler.oauth2.client.impl;

import io.gravitee.am.gateway.core.event.DomainEvent;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientServiceImpl extends AbstractService implements ClientService, InitializingBean, EventListener<DomainEvent, Domain> {

    private final Logger logger = LoggerFactory.getLogger(ClientServiceImpl.class);
    private ConcurrentMap<String, Set<Client>> domainsClients = new ConcurrentHashMap<>();

    @Autowired
    private Domain domain;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private EventManager eventManager;

    @Override
    public Maybe<Client> findByClientId(String clientId) {
        return findByDomainAndClientId(domain.getId(), clientId);
    }

    @Override
    public Maybe<Client> findByDomainAndClientId(String domain, String clientId) {
        return Observable.fromIterable(domainsClients.get(domain))
                .filter(client -> client.getClientId().equals(clientId))
                .firstElement();
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing clients for domain {}", domain.getName());
        clientRepository.findAll()
                .subscribe(
                        clients -> {
                            updateClients(clients);
                            logger.info("Clients loaded for domain {}", domain.getName());
                        },
                        error -> logger.error("Unable to initialize clients for domain {}", domain.getName(), error));

    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register clients event listener for cross domain events");
        eventManager.subscribeForEvents(this, DomainEvent.class);
    }

    @Override
    public void onEvent(Event<DomainEvent, Domain> event) {
        Domain updatedDomain = event.content();
        if (!updatedDomain.getId().equals(domain.getId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateDomainClients(updatedDomain);
                    break;
                case UNDEPLOY:
                    domainsClients.remove(updatedDomain.getId());
                    break;
            }
        }
    }

    private void updateDomainClients(Domain updatedDomain) {
        logger.info("Domain {} has received domain event from domain {}, update its clients", domain.getName(), updatedDomain.getName());
        clientRepository.findByDomain(updatedDomain.getId())
                .subscribe(
                        clients -> {
                            updateClients(clients);
                            logger.info("Clients updated for domain {}", updatedDomain.getName());
                        },
                        error -> logger.error("Unable to update clients for domain {}", domain.getName(), error));
    }

    private void updateClients(Set<Client> clients) {
        clients.forEach(client -> {
            Set<Client> existingDomainClients = domainsClients.get(client.getDomain());
            if (existingDomainClients != null) {
                Set<Client> updateClients = new HashSet<>(existingDomainClients);
                if (updateClients.contains(client)) {
                    updateClients.remove(client);
                }
                updateClients.add(client);
                domainsClients.put(client.getDomain(), updateClients);
            } else {
                domainsClients.put(client.getDomain(), Collections.singleton(client));
            }
        });
    }
}
