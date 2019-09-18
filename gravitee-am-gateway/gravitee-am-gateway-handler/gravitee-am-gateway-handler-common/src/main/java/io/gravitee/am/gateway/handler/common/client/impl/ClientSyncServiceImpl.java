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

import io.gravitee.am.gateway.core.event.ApplicationEvent;
import io.gravitee.am.gateway.core.event.EventManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.ClientService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class ClientSyncServiceImpl extends AbstractService implements ClientSyncService, InitializingBean, EventListener<ApplicationEvent, Payload> {

    private final Logger logger = LoggerFactory.getLogger(ClientSyncServiceImpl.class);
    private ConcurrentMap<String, Set<Client>> domainsClients = new ConcurrentHashMap<>();
    private ConcurrentMap<String, Set<Client>> domainsTemplates = new ConcurrentHashMap<>();

    @Autowired
    private Domain domain;

    @Autowired
    private ClientService clientService;

    @Autowired
    private EventManager eventManager;

    @Override
    public Maybe<Client> findById(String id) {
        return Observable.fromIterable(domainsClients.get(domain.getId()))
                .filter(client -> client.getId().equals(id))
                .firstElement();
    }

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
    public Single<List<Client>> findTemplates() {
        Set<Client> templates = this.domainsTemplates.get(domain.getId());
        return Single.just(templates==null?Collections.emptyList():new ArrayList<>(templates));
    }

    @Override
    public Client addDynamicClientRegistred(Client client) {
        this.updateClients(Collections.singleton(client));
        return client;
    }

    @Override
    public Client removeDynamicClientRegistred(Client client) {
        this.removeClient(client.getId(), client.getDomain());
        return client;
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing applications for domain {}", domain.getName());
        clientService.findAll()
                .subscribe(
                        clients -> {
                            updateClients(clients);
                            logger.info("Applications loaded for domain {}", domain.getName());
                        },
                        error -> logger.error("Unable to initialize applications for domain {}", domain.getName(), error));
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
    }

    @Override
    public void onEvent(Event<ApplicationEvent, Payload> event) {
        switch (event.type()) {
            case DEPLOY:
            case UPDATE:
                updateClient(event.content().getId(), event.type());
                break;
            case UNDEPLOY:
                removeClient(event.content().getId(), event.content().getDomain());
                break;
        }
    }

    private void updateClient(String appId, ApplicationEvent applicationEvent) {
        final String eventType = applicationEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} application event for {}", domain.getName(), eventType, appId);
        clientService.findById(appId)
                .subscribe(
                        client -> {
                            updateClients(Collections.singleton(client));
                            logger.info("Application {} {}d for domain {}", appId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} client for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No application found with id {}", appId));
    }

    private void removeClient(String idClient, String domainId) {
        logger.info("Domain {} has received application event, delete application {}", domain.getName(), idClient);
        if(domainsClients.get(domainId) != null) {
            domainsClients.get(domainId).removeIf(client -> client!=null && client.getId().equals(idClient));
        }
        if(domainsTemplates.get(domainId)!=null) {
            domainsTemplates.get(domainId).removeIf(template -> template!=null && template.getId().equals(idClient));
        }
    }

    private void updateClients(Set<Client> clients) {
        clients.forEach(client -> {
            this.updateClients(client);
            this.updateTemplates(client);
        });
    }

    private void updateClients(Client client) {
        Set<Client> existingDomainClients = domainsClients.get(client.getDomain());

        if (existingDomainClients != null) {
            Set<Client> updateClients = new HashSet<>(existingDomainClients);

            updateClients.remove(client);

            if(!client.isTemplate()) {
                updateClients.add(client);
            }
            domainsClients.put(client.getDomain(), updateClients);
        } else {
            if(!client.isTemplate()) {
                //Collections.singleton does not support removeIf
                domainsClients.put(client.getDomain(), new HashSet(Collections.singleton(client)));
            }
        }
    }

    private void updateTemplates(Client template) {
        Set<Client> existingDomainTemplates = domainsTemplates.get(template.getDomain());
        if (existingDomainTemplates != null) {
            Set<Client> updateTemplates = new HashSet<>(existingDomainTemplates);

            updateTemplates.remove(template);

            if(template.isTemplate()) {
                updateTemplates.add(template);
            }
            domainsTemplates.put(template.getDomain(), updateTemplates);
        } else {
            if(template.isTemplate()) {
                //Collections.singleton does not support removeIf
                domainsTemplates.put(template.getDomain(), new HashSet(Collections.singleton(template)));
            }
        }
    }
}
