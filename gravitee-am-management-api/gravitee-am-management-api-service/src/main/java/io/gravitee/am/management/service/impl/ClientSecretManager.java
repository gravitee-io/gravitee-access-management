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

import io.gravitee.am.common.event.ApplicationEvent;
import io.gravitee.am.management.service.ClientSecretNotifierService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClientSecretManager extends AbstractService<ClientSecretManager> implements EventListener<ApplicationEvent, Payload> {
    private static final Logger logger = LoggerFactory.getLogger(ClientSecretManager.class);

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private ClientSecretNotifierService clientSecretNotifierService;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for application events for the management API");
        eventManager.subscribeForEvents(this, ApplicationEvent.class);

        applicationService.findAll()
                .doOnNext(application -> logger.info("Initializing client secrets notifications for applicationId={}", application.getId()))
                .flatMapCompletable(this::initClientSecretNotifications)
                .subscribe();
    }

    @Override
    public void onEvent(Event<ApplicationEvent, Payload> event) {
        handle(event).subscribe();
    }

    Completable handle(Event<ApplicationEvent, Payload> event){
        return switch (event.type()) {
            case DEPLOY, UPDATE -> createAllClientSecretNotifications(event.content().getId());
            case UNDEPLOY -> removeAllClientSecretNotifications(event.content().getId());
        };
    }

    private Completable createAllClientSecretNotifications(String applicationId) {
        return applicationService.findById(applicationId)
                .flatMapCompletable(this::initClientSecretNotifications)
                .doOnError(error -> logger.error("Unable to init client secrets for applicationId={}", applicationId, error));
    }


    private Completable removeAllClientSecretNotifications(String applicationId) {
        return applicationService.findById(applicationId)
                .flattenAsFlowable(Application::getSecrets).map(ClientSecret::getId)
                .flatMapCompletable(clientSecretId -> clientSecretNotifierService.unregisterClientSecretExpiration(clientSecretId)
                        .andThen(clientSecretNotifierService.deleteClientSecretExpirationAcknowledgement(clientSecretId)));
    }

    private Completable initClientSecretNotifications(Application application) {
        return Flowable.fromIterable(application.getSecrets()).flatMapCompletable(secret ->
                clientSecretNotifierService.unregisterClientSecretExpiration(secret.getId())
                        .andThen(clientSecretNotifierService.registerClientSecretExpiration(application, secret)));
    }

}
