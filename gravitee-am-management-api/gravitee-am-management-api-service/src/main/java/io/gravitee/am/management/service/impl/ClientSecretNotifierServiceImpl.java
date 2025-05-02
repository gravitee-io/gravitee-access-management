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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.gravitee.am.management.service.ClientSecretNotifierService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.impl.notifications.EmailNotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.ExpireThresholdsNotificationCondition;
import io.gravitee.am.management.service.impl.notifications.ExpireThresholdsResendNotificationCondition;
import io.gravitee.am.management.service.impl.notifications.ManagementUINotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils;
import io.gravitee.am.management.service.impl.notifications.NotifierSettings;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.node.api.notifier.NotificationDefinition;
import io.gravitee.node.api.notifier.NotifierService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_EMAIL_NOTIFIER;
import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_LOG_NOTIFIER;
import static io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils.TYPE_UI_NOTIFIER;

@Component
public class ClientSecretNotifierServiceImpl implements ClientSecretNotifierService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSecretNotifierServiceImpl.class);
    public static final String RESOURCE_TYPE = "application/secret";

    private static final ManagementUINotifierConfiguration UI_NOTIFICATION_CFG = ManagementUINotifierConfiguration.clientSecretExpiration();

    @Value("${notifiers.ui.enabled:true}")
    boolean uiNotifierEnabled;

    @Value("${notifiers.email.enabled:false}")
    boolean emailNotifierEnabled;

    @Value("${notifiers.log.enabled:true}")
    boolean isLogNotifierEnabled;

    @Autowired
    @Qualifier("clientSecretNotifierSettings")
    private NotifierSettings clientSecretNotifierSettings;

    @Autowired
    private NotifierService notifierService;

    @Autowired
    private EmailNotifierConfiguration emailConfiguration;

    @Autowired
    private EmailService emailService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DomainOwnersProvider domainOwnersProvider;


    @Override
    public Completable registerClientSecretExpiration(Application application, ClientSecret clientSecret) {
        if (clientSecretNotifierSettings.enabled()) {
            return findDomain(application.getDomain())
                    .flatMapPublisher(domain ->
                            domainOwnersProvider.retrieveDomainOwners(domain)
                                    .flatMap(user -> {
                                        var emailNotificationDef = buildEmailNotificationDefinition(domain, application, clientSecret, user).toFlowable();
                                        var uiNotificationDef = buildUINotificationDefinition(domain, application, clientSecret, user).toFlowable();
                                        var logNotificationDef = buildLogNotificationDefinition(domain, application, clientSecret).toFlowable();
                                        return Flowable.mergeArray(emailNotificationDef, uiNotificationDef, logNotificationDef);
                                    }))
                    .flatMapCompletable(definition -> Completable.fromRunnable(() -> notifierService.register(definition,
                            new ExpireThresholdsNotificationCondition(clientSecretNotifierSettings.expiryThresholds()),
                            new ExpireThresholdsResendNotificationCondition(clientSecretNotifierSettings.expiryThresholds()))));
        } else {
            return Completable.complete();
        }
    }


    @Override
    public Completable unregisterClientSecretExpiration(String clientSecretId) {
        if (clientSecretNotifierSettings.enabled()) {
            return Completable.fromRunnable(() -> this.notifierService.unregisterAll(clientSecretId, RESOURCE_TYPE));
        } else {
            return Completable.complete();
        }
    }

    @Override
    public Completable deleteClientSecretExpirationAcknowledgement(String clientSecretId) {
        if (clientSecretNotifierSettings.enabled()) {
            LOGGER.debug("Remove All NotificationAcknowledge for the client secret {}", clientSecretId);
            return this.notifierService.deleteAcknowledge(clientSecretId, RESOURCE_TYPE);
        } else {
            return Completable.complete();
        }
    }

    private Single<Domain> findDomain(String domainId) {
        return domainService.findById(domainId)
                .switchIfEmpty(Single.error(new DomainNotFoundException(domainId)));
    }

    private Maybe<NotificationDefinition> buildUINotificationDefinition(Domain domain, Application application, ClientSecret clientSecret, User user) {
        if (uiNotifierEnabled) {
            try {
                Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                        .withClientSecret(clientSecret)
                        .withDomain(domain)
                        .withUser(user)
                        .withApplication(application)
                        .build();


                final NotificationDefinition definition = new NotificationDefinition();
                definition.setType(TYPE_UI_NOTIFIER);
                definition.setConfiguration(mapper.writeValueAsString(UI_NOTIFICATION_CFG));
                definition.setResourceId(clientSecret.getId());
                definition.setResourceType(RESOURCE_TYPE);
                definition.setAudienceId(user.getId());
                definition.setCron(clientSecretNotifierSettings.cronExpression());
                definition.setData(data);

                return Maybe.just(definition);
            } catch (IOException e) {
                LOGGER.warn("Unable to generate ui configuration for clientSecret expiration", e);
            }
        } else {
            LOGGER.debug("Ignore email notification for clientSecret {}, email is disabled or email address is missing", clientSecret.getId());
        }
        return Maybe.empty();
    }

    private Maybe<NotificationDefinition> buildEmailNotificationDefinition(Domain domain, Application application, ClientSecret clientSecret, User user) {
        if (emailNotifierEnabled && !Strings.isNullOrEmpty(user.getEmail())) {
            Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                    .withDomain(domain)
                    .withUser(user)
                    .withApplication(application)
                    .withClientSecret(clientSecret)
                    .build();

            return emailService.getFinalEmail(domain, null, Template.CLIENT_SECRET_EXPIRATION, user, data)
                    .map(email -> {
                        EmailNotifierConfiguration notifierConfig = new EmailNotifierConfiguration(this.emailConfiguration);
                        notifierConfig.setSubject(email.getSubject());
                        notifierConfig.setBody(email.getContent());
                        notifierConfig.setTo(user.getEmail());

                        final NotificationDefinition definition = new NotificationDefinition();
                        definition.setType(TYPE_EMAIL_NOTIFIER);
                        definition.setConfiguration(mapper.writeValueAsString(notifierConfig));
                        definition.setResourceId(clientSecret.getId());
                        definition.setResourceType(RESOURCE_TYPE);
                        definition.setAudienceId(user.getId());
                        definition.setCron(clientSecretNotifierSettings.cronExpression());
                        definition.setData(data);

                        return definition;
                    });
        } else {
            LOGGER.debug("Ignore email notification for client secret {}, email is disabled or email address is missing", clientSecret.getId());
        }
        return Maybe.empty();
    }

    private Maybe<NotificationDefinition> buildLogNotificationDefinition(Domain domain, Application application, ClientSecret clientSecret) {
        if (isLogNotifierEnabled) {
            final Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                    .withDomain(domain)
                    .withApplication(application)
                    .withClientSecret(clientSecret)
                    .build();

            final NotificationDefinition definition = new NotificationDefinition();
            definition.setType(TYPE_LOG_NOTIFIER);
            definition.setResourceId(clientSecret.getId());
            definition.setResourceType(RESOURCE_TYPE);
            definition.setCron(clientSecretNotifierSettings.cronExpression());
            definition.setData(data);

            return Maybe.just(definition);
        } else {
            LOGGER.debug("Ignoring log notification for client secret {}, log notification is disabled.", clientSecret.getId());
        }

        return Maybe.empty();
    }

}
