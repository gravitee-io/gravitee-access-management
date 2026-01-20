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

import io.gravitee.am.common.event.ProtectedResourceSecretEvent;
import io.gravitee.am.management.service.ClientSecretNotifierService;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.exception.ClientSecretNotFoundException;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;

@Component
public class ProtectedResourceSecretManager extends AbstractService<ProtectedResourceSecretManager> implements EventListener<ProtectedResourceSecretEvent, Payload> {
    private static final Logger logger = LoggerFactory.getLogger(ProtectedResourceSecretManager.class);

    @Autowired
    private ProtectedResourceService protectedResourceService;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private ClientSecretNotifierService clientSecretNotifierService;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for protected resource secret events for the management API");
        eventManager.subscribeForEvents(this, ProtectedResourceSecretEvent.class);

        protectedResourceService.findAll()
                .doOnNext(resource -> logger.info("Initializing client secrets notifications for protected resource={}", resource.getId()))
                .flatMapCompletable(this::initClientSecretNotifications)
                .subscribe();
    }

    @Override
    public void onEvent(Event<ProtectedResourceSecretEvent, Payload> event) {
        handle(event).subscribe();
    }

    Completable handle(Event<ProtectedResourceSecretEvent, Payload> event) {
        return switch (event.type()) {
            case CREATE -> createClientSecretNotifications(event.content());
            case RENEW -> renewClientSecretNotifications(event.content());
            case DELETE -> removeClientSecretNotifications(event.content().getId());
        };
    }

    private Completable createClientSecretNotifications(Payload payload) {
        return protectedResourceService.findById(payload.getReferenceId())
                .flatMapCompletable(resource -> findSecret(resource, payload.getId())
                        .flatMapCompletable(secret -> clientSecretNotifierService.registerClientSecretExpiration(resource, secret)));
    }

    private Completable renewClientSecretNotifications(Payload payload) {
        return protectedResourceService.findById(payload.getReferenceId())
                .flatMapCompletable(resource -> findSecret(resource, payload.getId())
                        .flatMapCompletable(secret -> removeClientSecretNotifications(secret.getId())
                                .andThen(clientSecretNotifierService.registerClientSecretExpiration(resource, secret))));
    }

    private Completable removeClientSecretNotifications(String clientSecretId) {
        return clientSecretNotifierService.unregisterClientSecretExpiration(clientSecretId)
                .andThen(clientSecretNotifierService.deleteClientSecretExpirationAcknowledgement(clientSecretId));
    }

    private Completable initClientSecretNotifications(ProtectedResource resource) {
        return Flowable.fromIterable(Optional.ofNullable(resource.getClientSecrets()).orElse(Collections.emptyList()))
                .flatMapCompletable(secret ->
                        clientSecretNotifierService.unregisterClientSecretExpiration(secret.getId())
                                .andThen(clientSecretNotifierService.registerClientSecretExpiration(resource, secret)));
    }

    private Single<ClientSecret> findSecret(ProtectedResource resource, String secretId) {
        return Single.fromCallable(() ->
                Optional.ofNullable(resource.getClientSecrets())
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(s -> s.getId().equals(secretId))
                        .findFirst()
                        .orElseThrow(() -> new ClientSecretNotFoundException(secretId))
        );
    }
}
