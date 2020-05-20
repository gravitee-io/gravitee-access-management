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

import io.gravitee.am.repository.management.api.ClientRepository;
import io.gravitee.am.service.ClientService;
import io.reactivex.Completable;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ClientsToApplicationsUpgrader implements Upgrader, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientsToApplicationsUpgrader.class);
    private static final String AM_V2_VERSION = "AM_V2_VERSION";

    // use repository instead of service to fetch the remaining old clients
    @Lazy
    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientService clientService;

    @Override
    public boolean upgrade() {
        LOGGER.info("Applying clients to applications upgrade");

        clientRepository.collectionExists()
                .flatMapCompletable(collectionExists -> {
                    if (collectionExists) {
                        LOGGER.info("Clients collection exists, update clients to applications ...");
                        return clientRepository.findAll()
                                .flatMapObservable(clients -> Observable.fromIterable(clients))
                                .flatMapSingle(client -> {
                                    LOGGER.info("Update client : {} - {}", client.getId(), client.getClientId());
                                    if (client.getSoftwareVersion() == null || client.getSoftwareVersion().isEmpty()) {
                                        client.setSoftwareVersion(AM_V2_VERSION);
                                    }
                                    return clientService.create(client)
                                            .doOnSuccess(client1 -> LOGGER.info("Client : {} - {} successfully updated", client.getId(), client.getClientId()))
                                            .doOnError(ex -> LOGGER.error("Failed to update client : {} - {}", client.getId(), client.getClientId()));
                                })
                                .toList()
                                .toCompletable()
                                .andThen(clientRepository.deleteCollection());
                    } else {
                        LOGGER.info("Clients collection doesn't exist, skip upgrade");
                        return Completable.complete();
                    }
                })
                .subscribe(
                        () -> LOGGER.info("Clients to applications upgrade, done."),
                        error -> LOGGER.error("An error occurs while updating clients to applications", error)
                );

        return true;
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
