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
import io.gravitee.am.management.service.CertificateNotifierService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.impl.notifications.EmailNotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.ExpireThresholdsNotificationCondition;
import io.gravitee.am.management.service.impl.notifications.ExpireThresholdsResendNotificationCondition;
import io.gravitee.am.management.service.impl.notifications.ManagementUINotifierConfiguration;
import io.gravitee.am.management.service.impl.notifications.NotificationDefinitionUtils;
import io.gravitee.am.management.service.impl.notifications.NotifierSettings;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
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

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class CertificateNotifierServiceImpl implements CertificateNotifierService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateNotifierServiceImpl.class);
    public static final String RESOURCE_TYPE_CERTIFICATE = "certificate";

    @Value("${notifiers.email.enabled:false}")
    private boolean emailNotifierEnabled;

    @Value("${notifiers.ui.enabled:true}")
    private boolean uiNotifierEnabled;

    @Value("${notifiers.log.enabled:true}")
    private boolean isLogNotifierEnabled;

    @Autowired
    private NotifierService notifierService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private EmailNotifierConfiguration emailConfiguration;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DomainOwnersProvider domainOwnersProvider;

    @Autowired
    @Qualifier("certificateNotifierSettings")
    private NotifierSettings certificateNotifierSettings;


    @Override
    public void registerCertificateExpiration(Certificate certificate) {
        if (certificateNotifierSettings.enabled()) {
            findDomain(certificate.getDomain())
                    .flatMapPublisher(domain ->
                            domainOwnersProvider.retrieveDomainOwners(domain)
                                    .flatMap(user -> {
                                        final Flowable<NotificationDefinition> emailNotificationDef = buildEmailNotificationDefinition(certificate, domain, user).toFlowable();
                                        final Flowable<NotificationDefinition> uiNotificationDef = buildUINotificationDefinition(certificate, domain, user).toFlowable();
                                        final Flowable<NotificationDefinition> logNotificationDef = buildLogNotificationDefinition(certificate, domain).toFlowable();
                                        return Flowable.mergeArray(emailNotificationDef, uiNotificationDef, logNotificationDef);
                                    }))
                    .subscribe(definition ->
                        notifierService.register(definition,
                            new ExpireThresholdsNotificationCondition(certificateNotifierSettings.expiryThresholds()),
                            new ExpireThresholdsResendNotificationCondition(certificateNotifierSettings.expiryThresholds()))
                    );
        }
    }

    @Override
    public void unregisterCertificateExpiration(String domainId, String certificateId) {
        if (certificateNotifierSettings.enabled()) {
            this.notifierService.unregisterAll(certificateId, RESOURCE_TYPE_CERTIFICATE);
        }
    }

    @Override
    public Completable deleteCertificateExpirationAcknowledgement(String certificateId) {
        if (certificateNotifierSettings.enabled()) {
            LOGGER.debug("Remove All NotificationAcknowledge for the certificate {}", certificateId);
            return this.notifierService.deleteAcknowledge(certificateId, RESOURCE_TYPE_CERTIFICATE);
        } else {
            return Completable.complete();
        }
    }




    private Single<Domain> findDomain(String domainId) {
        return domainService.findById(domainId)
                .switchIfEmpty(Single.error(new DomainNotFoundException(domainId)));
    }



    private Maybe<NotificationDefinition> buildEmailNotificationDefinition(Certificate certificate, Domain domain, User user) {
        if (emailNotifierEnabled && !Strings.isNullOrEmpty(user.getEmail())) {
            Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                    .withDomain(domain)
                    .withUser(user)
                    .withCertificate(certificate)
                    .build();

            return emailService.getFinalEmail(domain, null, Template.CERTIFICATE_EXPIRATION, user, data)
                    .map(email -> {
                        EmailNotifierConfiguration notifierConfig = new EmailNotifierConfiguration(this.emailConfiguration);
                        notifierConfig.setSubject(email.getSubject());
                        notifierConfig.setBody(email.getContent());
                        notifierConfig.setTo(user.getEmail());

                        final NotificationDefinition definition = new NotificationDefinition();
                        definition.setType(TYPE_EMAIL_NOTIFIER);
                        definition.setConfiguration(mapper.writeValueAsString(notifierConfig));
                        definition.setResourceId(certificate.getId());
                        definition.setResourceType(RESOURCE_TYPE_CERTIFICATE);
                        definition.setAudienceId(user.getId());
                        definition.setCron(certificateNotifierSettings.cronExpression());
                        definition.setData(data);

                        return definition;
                    });
        } else {
            LOGGER.debug("Ignore email notification for certificate {}, email is disabled or email address is missing", certificate.getId());
        }
        return Maybe.empty();
    }

    private Maybe<NotificationDefinition> buildUINotificationDefinition(Certificate certificate, Domain domain, User user) {
        if (uiNotifierEnabled) {
            try {
                Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                        .withDomain(domain)
                        .withUser(user)
                        .withCertificate(certificate)
                        .build();

                final NotificationDefinition definition = new NotificationDefinition();
                definition.setType(TYPE_UI_NOTIFIER);
                definition.setConfiguration(mapper.writeValueAsString(ManagementUINotifierConfiguration.certificateExpiration()));
                definition.setResourceId(certificate.getId());
                definition.setResourceType(RESOURCE_TYPE_CERTIFICATE);
                definition.setAudienceId(user.getId());
                definition.setCron(certificateNotifierSettings.cronExpression());
                definition.setData(data);

                return Maybe.just(definition);
            } catch (IOException e) {
                LOGGER.warn("Unable to generate ui configuration for certificate expiration", e);
            }
        } else {
            LOGGER.debug("Ignore email notification for certificate {}, email is disabled or email address is missing", certificate.getId());
        }
        return Maybe.empty();
    }

    private Maybe<NotificationDefinition> buildLogNotificationDefinition(Certificate certificate, Domain domain) {
        if (isLogNotifierEnabled) {
            final Map<String, Object> data = new NotificationDefinitionUtils.ParametersBuilder()
                    .withDomain(domain)
                    .withCertificate(certificate)
                    .build();

            final NotificationDefinition definition = new NotificationDefinition();
            definition.setType(TYPE_LOG_NOTIFIER);
            definition.setResourceId(certificate.getId());
            definition.setResourceType(RESOURCE_TYPE_CERTIFICATE);
            definition.setCron(certificateNotifierSettings.cronExpression());
            definition.setData(data);

            return Maybe.just(definition);
        } else {
            LOGGER.debug("Ignoring log notification for certificate {}, log notification is disabled.", certificate.getId());
        }

        return Maybe.empty();
    }
}
