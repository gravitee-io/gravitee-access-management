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

import io.gravitee.am.management.service.CertificateNotifierService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.impl.notifications.ExpireThresholdsNotificationCondition;
import io.gravitee.am.management.service.impl.notifications.ExpireThresholdsResendNotificationCondition;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.am.management.service.impl.notifications.definition.NotificationDefinitionFactory;
import io.gravitee.am.management.service.impl.notifications.definition.CertificateNotifierSubject;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
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

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class CertificateNotifierServiceImpl implements CertificateNotifierService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateNotifierServiceImpl.class);

    @Autowired
    private NotifierService notifierService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private DomainOwnersProvider domainOwnersProvider;

    @Autowired
    @Qualifier("certificateNotifierSettings")
    private NotifierSettings certificateNotifierSettings;

    @Autowired
    private List<NotificationDefinitionFactory<CertificateNotifierSubject>> notificationDefinitionFactories;


    @Override
    public void registerCertificateExpiration(Certificate certificate) {
        if (certificateNotifierSettings.enabled() && certificate.getExpiresAt() != null) {
            findDomain(certificate.getDomain())
                    .flatMapPublisher(domain ->
                                    domainOwnersProvider.retrieveDomainOwners(domain)
                                            .map(user -> new CertificateNotifierSubject(certificate, domain, user))
                                            .flatMap(subject -> Flowable.fromIterable(notificationDefinitionFactories)
                                                    .flatMapMaybe(factory -> factory.buildNotificationDefinition(subject))))
                    .subscribe(definition ->
                        notifierService.register(definition,
                            new ExpireThresholdsNotificationCondition(certificateNotifierSettings.expiryThresholds()),
                            new ExpireThresholdsResendNotificationCondition(certificateNotifierSettings.expiryThresholds()))
                    );
        }
    }

    private Single<Domain> findDomain(String domainId) {
        return domainService.findById(domainId)
                .switchIfEmpty(Single.error(new DomainNotFoundException(domainId)));
    }

    @Override
    public void unregisterCertificateExpiration(String domainId, String certificateId) {
        if (certificateNotifierSettings.enabled()) {
            this.notifierService.unregisterAll(certificateId, CertificateNotifierSubject.RESOURCE_TYPE);
        }
    }

    @Override
    public Completable deleteCertificateExpirationAcknowledgement(String certificateId) {
        if (certificateNotifierSettings.enabled()) {
            LOGGER.debug("Remove All NotificationAcknowledge for the certificate {}", certificateId);
            return this.notifierService.deleteAcknowledge(certificateId, CertificateNotifierSubject.RESOURCE_TYPE);
        } else {
            return Completable.complete();
        }
    }

}
