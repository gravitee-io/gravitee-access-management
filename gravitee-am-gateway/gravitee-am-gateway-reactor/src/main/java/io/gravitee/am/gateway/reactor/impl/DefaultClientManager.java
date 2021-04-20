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
package io.gravitee.am.gateway.reactor.impl;

import io.gravitee.am.gateway.core.manager.EntityManager;
import io.gravitee.am.model.oidc.Client;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultClientManager implements EntityManager<Client> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultClientManager.class);
    private final ConcurrentMap<String, Client> clients = new ConcurrentHashMap<>();

    @Override
    public void deploy(Client client) {
        clients.put(client.getId(), client);
        logger.info("Client {} for domain {} loaded", client.getId(), client.getDomain());
    }

    @Override
    public void update(Client client) {
        clients.put(client.getId(), client);
        logger.info("Client {} for domain {} updated", client.getId(), client.getDomain());
    }

    @Override
    public void undeploy(String clientId) {
        clients.remove(clientId);
        logger.info("Client {} undeployed", clientId);
    }

    @Override
    public Collection<Client> entities() {
        return clients.values();
    }

    @Override
    public Client get(String clientId) {
        return clients.get(clientId);
    }

    public void init(Collection<Client> clients) {
        clients.forEach(c -> this.clients.put(c.getId(), c));
    }
}
