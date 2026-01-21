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
package io.gravitee.am.management.service.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.impl.notifications.EmailNotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.KafkaNotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.PlatformNotifierPluginFactoryImpl;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.am.management.service.impl.notifications.definition.UiNotificationDefinitionFactory;
import io.gravitee.am.management.service.impl.notifications.definition.EmailNotificationDefinitionFactory;
import io.gravitee.am.management.service.impl.notifications.definition.KafkaNotificationDefinitionFactory;
import io.gravitee.am.management.service.impl.notifications.definition.LoggerNotificationDefinitionFactory;
import io.gravitee.am.management.service.impl.notifications.definition.ClientSecretNotifierSubject;
import io.gravitee.am.management.service.impl.notifications.definition.NotificationDefinitionFactory;
import io.gravitee.am.management.service.impl.notifications.definition.CertificateNotifierSubject;
import io.gravitee.am.management.service.impl.notifications.template.FreemarkerTemplateProvider;
import io.gravitee.am.management.service.impl.notifications.template.TemplateProvider;
import io.gravitee.node.api.notifier.NotifierService;
import io.gravitee.node.notifier.NotifierServiceImpl;
import io.gravitee.node.notifier.plugin.NotifierPluginFactory;
import io.gravitee.node.notifier.spring.NotifierConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.gravitee.am.management.service.impl.notifications.definition.NotificationDefinitionFactory.stub;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class PlatformNotifierConfiguration extends NotifierConfiguration {

    @Bean
    @Override
    public NotifierPluginFactory getNotifierPluginFactory() {
        return new PlatformNotifierPluginFactoryImpl();
    }

    @Bean
    public NotifierService notifierService() {
        return new NotifierServiceImpl();
    }

    @Bean
    @Qualifier("uiNotificationTemplateProvider")
    public TemplateProvider uiNotificationTemplateProvider(@Value("${notifiers.ui.templates.path:${gravitee.home}/templates/notifications/management}") String templatesPath) {
        return new FreemarkerTemplateProvider(templatesPath, ".yml");
    }

    @Bean
    @Qualifier("kafkaNotificationTemplateProvider")
    public TemplateProvider kafkaNotificationTemplateProvider(@Value("${notifiers.kafka.templates.path:${gravitee.home}/templates/notifications/kafka}") String templatesPath) {
        return new FreemarkerTemplateProvider(templatesPath, ".txt");
    }

    @Bean
    public NotificationDefinitionFactory<CertificateNotifierSubject>  certificateEmailNotificationDefinitionFactory(
            EmailService emailService,
            EmailNotifierConfiguration emailConfiguration,
            ObjectMapper mapper,
            @Qualifier("certificateNotifierSettings") NotifierSettings notifierSettings,
            @Value("${notifiers.email.enabled:false}") boolean enabled) {
        if(enabled) {
            return new EmailNotificationDefinitionFactory<>(emailService, emailConfiguration, mapper, notifierSettings);
        } else {
            return stub();
        }
    }

    @Bean
    public NotificationDefinitionFactory<CertificateNotifierSubject> certificateKafkaNotificationDefinitionFactory(
            ObjectMapper mapper,
            KafkaNotifierConfiguration kafkaNotifierConfiguration,
            @Qualifier("certificateNotifierSettings") NotifierSettings notifierSettings,
            @Qualifier("kafkaNotificationTemplateProvider") TemplateProvider templateProvider,
            @Value("${notifiers.kafka.enabled:false}") boolean enabled) {
        if(enabled) {
            return new KafkaNotificationDefinitionFactory<>(mapper, kafkaNotifierConfiguration, templateProvider, notifierSettings);
        } else {
            return stub();
        }
    }

    @Bean
    public NotificationDefinitionFactory<CertificateNotifierSubject> certificateLoggerNotificationDefinitionFactory(
             @Qualifier("certificateNotifierSettings") NotifierSettings notifierSettings,
             @Value("${notifiers.log.enabled:true}") boolean enabled) {
        if(enabled) {
            return new LoggerNotificationDefinitionFactory<>(notifierSettings,
                    obj -> "Certificate %s of domain %s expires on %s".formatted(
                            obj.getCertificate().getName(),
                            obj.getDomain().getName(),
                            obj.getCertificate().getExpiresAt()));
        } else {
            return stub();
        }
    }

    @Bean
    public NotificationDefinitionFactory<CertificateNotifierSubject> certificateUiNotificationDefinitionFactory(
            ObjectMapper mapper,
            @Qualifier("certificateNotifierSettings") NotifierSettings notifierSettings,
            @Value("${notifiers.ui.enabled:false}") boolean enabled) {
        if(enabled) {
            return new UiNotificationDefinitionFactory<>(mapper, notifierSettings);
        } else {
            return stub();
        }
    }

    @Bean
    public NotificationDefinitionFactory<ClientSecretNotifierSubject> clientSecretEmailNotificationDefinitionFactory(
            EmailService emailService,
            EmailNotifierConfiguration emailConfiguration,
            ObjectMapper mapper,
            @Qualifier("clientSecretNotifierSettings") NotifierSettings notifierSettings,
            @Value("${notifiers.email.enabled:false}") boolean enabled) {
        if(enabled) {
            return new EmailNotificationDefinitionFactory<>(emailService, emailConfiguration, mapper, notifierSettings);
        } else {
            return stub();
        }
    }

    @Bean
    public NotificationDefinitionFactory<ClientSecretNotifierSubject> clientSecretKafkaNotificationDefinitionFactory(
            ObjectMapper mapper,
            KafkaNotifierConfiguration kafkaNotifierConfiguration,
            @Qualifier("clientSecretNotifierSettings") NotifierSettings notifierSettings,
            @Qualifier("kafkaNotificationTemplateProvider") TemplateProvider templateProvider,
            @Value("${notifiers.kafka.enabled:false}") boolean enabled) {
        if(enabled) {
            return new KafkaNotificationDefinitionFactory<>(mapper, kafkaNotifierConfiguration, templateProvider, notifierSettings);
        } else {
            return stub();
        }
    }

    @Bean
    public NotificationDefinitionFactory<ClientSecretNotifierSubject> clientSecretLoggerNotificationDefinitionFactory(
            @Qualifier("clientSecretNotifierSettings") NotifierSettings notifierSettings,
            @Value("${notifiers.log.enabled:true}") boolean enabled) {
        if(enabled) {
            return new LoggerNotificationDefinitionFactory<>(notifierSettings,
                    obj -> "Client Secret %s of %s %s in domain %s expires on %s".formatted(
                            obj.getClientSecret().getName(),
                            obj.getApplication() != null ? "application" : "protected resource",
                            obj.getApplication() != null ? obj.getApplication().getName() : obj.getProtectedResource().getName(),
                            obj.getDomain().getName(),
                            obj.getClientSecret().getExpiresAt()));
        } else {
            return stub();
        }
    }

    @Bean
    public NotificationDefinitionFactory<ClientSecretNotifierSubject> clientSecretUiNotificationDefinitionFactory(
            ObjectMapper mapper,
            @Qualifier("clientSecretNotifierSettings") NotifierSettings notifierSettings,
            @Value("${notifiers.ui.enabled:false}") boolean enabled) {
        if(enabled) {
            return new UiNotificationDefinitionFactory<>(mapper, notifierSettings);
        } else {
            return stub();
        }
    }

}
