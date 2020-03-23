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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.model.PatchClient;
import io.gravitee.am.service.utils.ResponseTypeUtils;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
// TODO
public class ClientsUpgrader implements Upgrader, Ordered {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientsUpgrader.class);

    @Autowired
    private DomainService domainService;

    @Autowired
    private ClientService clientService;

    @Override
    public boolean upgrade() {
        LOGGER.info("Applying clients upgrade");

        domainService.findAll()
                .flatMapObservable(domains -> Observable.fromIterable(domains))
                .flatMapSingle(this::upgradeClients)
                .subscribe();

        return true;
    }

    private Single<List<Client>> upgradeClients(Domain domain) {
        return clientService.findByDomain(domain.getId())
                .filter(clients -> clients != null)
                .flatMapObservable(clients -> Observable.fromIterable(clients))
                .filter(this::mustBeUpgraded)
                .flatMapSingle(this::upgradeClient)
                .toList();
    }

    private Single<Client> upgradeClient(Client client) {
        //Set default client name if not exists
        if(client.getClientName()==null || client.getClientName().trim().isEmpty()) {
            PatchClient patch = new PatchClient();
            patch.setClientName(Optional.of("Unknown Client"));

            //response_type have been introduced in the same time than client_name, we set it only if client_name is being upgraded.
            if(client.getAuthorizedGrantTypes()!=null && !client.getAuthorizedGrantTypes().isEmpty()) {
                Set<String> responseTypes = ResponseTypeUtils.applyDefaultResponseType(client.getAuthorizedGrantTypes());
                patch.setResponseTypes(Optional.of(responseTypes.stream().collect(Collectors.toList())));
            }
            return clientService.patch(client.getDomain(), client.getId(),patch);
        }
        //Nothing to update
        return Single.just(client);
    }

    private boolean mustBeUpgraded(Client client) {
        return client.getResponseTypes()==null ||
                client.getResponseTypes().isEmpty() ||
                client.getClientName()==null ||
                client.getClientName().trim().isEmpty();
    }

    @Override
    public int getOrder() {
        return 164;
    }
}
