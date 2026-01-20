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

import io.gravitee.am.management.service.ClientSecretNotifierService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.impl.notifications.ExpireThresholdsNotificationCondition;
import io.gravitee.am.management.service.impl.notifications.ExpireThresholdsResendNotificationCondition;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.am.management.service.impl.notifications.definition.NotificationDefinitionFactory;
import io.gravitee.am.management.service.impl.notifications.definition.ClientSecretNotifierSubject;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.node.api.notifier.NotifierService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClientSecretNotifierServiceImpl implements ClientSecretNotifierService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSecretNotifierServiceImpl.class);

    @Autowired
    @Qualifier("clientSecretNotifierSettings")
    private NotifierSettings clientSecretNotifierSettings;

    @Autowired
    private NotifierService notifierService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private DomainOwnersProvider domainOwnersProvider;

    @Autowired
    private List<NotificationDefinitionFactory<ClientSecretNotifierSubject>> notificationDefinitionFactories;

    @Override
    public Completable registerClientSecretExpiration(Application application, ClientSecret clientSecret) {
        if (clientSecretNotifierSettings.enabled() && clientSecret.getExpiresAt() != null) {
            return findDomain(application.getDomain())
                    .flatMapPublisher(domain ->
                            domainOwnersProvider.retrieveDomainOwners(domain)
                                    .map(user -> new ClientSecretNotifierSubject(clientSecret, application, domain, user))
                                    .flatMap(subject -> Flowable.fromIterable(notificationDefinitionFactories)
                                            .flatMapMaybe(factory -> factory.buildNotificationDefinition(subject))))
                    .flatMapCompletable(definition -> Completable.fromRunnable(() -> notifierService.register(definition,
                            new ExpireThresholdsNotificationCondition(clientSecretNotifierSettings.expiryThresholds()),
                            new ExpireThresholdsResendNotificationCondition(clientSecretNotifierSettings.expiryThresholds()))));
        } else {
            return Completable.complete();
        }
    }

    @Override
    public Completable registerClientSecretExpiration(ProtectedResource protectedResource, ClientSecret clientSecret) {
        if (clientSecretNotifierSettings.enabled() && clientSecret.getExpiresAt() != null) {
            return findDomain(protectedResource.getDomainId())
                    .flatMapPublisher(domain ->
                            domainOwnersProvider.retrieveDomainOwners(domain)
                                    .map(user -> new ClientSecretNotifierSubject(clientSecret, protectedResource, domain, user))
                                    .flatMap(subject -> Flowable.fromIterable(notificationDefinitionFactories)
                                            .flatMapMaybe(factory -> factory.buildNotificationDefinition(subject))))
                    .flatMapCompletable(definition -> Completable.fromRunnable(() -> notifierService.register(definition,
                            new ExpireThresholdsNotificationCondition(clientSecretNotifierSettings.expiryThresholds()),
                            new ExpireThresholdsResendNotificationCondition(clientSecretNotifierSettings.expiryThresholds()))));
        } else {
            return Completable.complete();
        }
    }

    private Single<Domain> findDomain(String domainId) {
        return domainService.findById(domainId)
                .switchIfEmpty(Single.error(new DomainNotFoundException(domainId)));
    }


    @Override
    public Completable unregisterClientSecretExpiration(String clientSecretId) {
        if (clientSecretNotifierSettings.enabled()) {
            return Completable.fromRunnable(() -> this.notifierService.unregisterAll(clientSecretId, ClientSecretNotifierSubject.RESOURCE_TYPE));
        } else {
            return Completable.complete();
        }
    }

    @Override
    public Completable deleteClientSecretExpirationAcknowledgement(String clientSecretId) {
        if (clientSecretNotifierSettings.enabled()) {
            LOGGER.debug("Remove All NotificationAcknowledge for the client secret {}", clientSecretId);
            return this.notifierService.deleteAcknowledge(clientSecretId, ClientSecretNotifierSubject.RESOURCE_TYPE);
        } else {
            return Completable.complete();
        }
    }

}
