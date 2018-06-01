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

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientServiceImpl implements ClientService, InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(ClientServiceImpl.class);
    private Map<String, Client> clients = new HashMap<>();

    @Autowired
    private Domain domain;

    @Autowired
    private ClientRepository clientRepository;

    @Override
    public Maybe<Client> findByClientId(String clientId) {
        Client client = clients.get(clientId);
        return (client != null) ? Maybe.just(client) : Maybe.empty();
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing clients for domain {}", domain.getName());
        clientRepository.findByDomain(domain.getId())
                .doOnSuccess(clients1 -> clients1.forEach(client -> clients.put(client.getClientId(), client)))
                .subscribe(
                        result -> logger.info("Clients loaded for domain {}", domain.getName()),
                        error -> logger.error("Unable to initialize clients for domain {}", domain.getName(), error)
                );

    }
}
