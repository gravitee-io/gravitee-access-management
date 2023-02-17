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

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.common.event.CertificateEvent;
import io.gravitee.am.management.service.CertificateManager;
import io.gravitee.am.management.service.DomainNotifierService;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.plugins.certificate.core.CertificateProviderConfiguration;
import io.gravitee.am.service.CertificateService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class CertificateManagerImpl extends AbstractService<CertificateManager> implements CertificateManager, EventListener<CertificateEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(CertificateManagerImpl.class);
    private static final long retryTimeout = 10000;
    private ConcurrentMap<String, CertificateProvider> certificateProviders = new ConcurrentHashMap<>();

    @Autowired
    private CertificatePluginManager certificatePluginManager;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private DomainNotifierService notifierService;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for certificate events for the management API");
        eventManager.subscribeForEvents(this, CertificateEvent.class);

        logger.info("Initializing certificate providers");
        certificateService.findAll().blockingIterable().forEach(certificate -> {
            logger.info("\tInitializing certificate: {} [{}]", certificate.getName(), certificate.getType());
            loadCertificate(certificate);
        });
    }

    @Override
    public void onEvent(Event<CertificateEvent, Payload> event) {
        switch (event.type()) {
            case DEPLOY:
                deployCertificate(event.content().getId());
                break;
            case UPDATE:
                updateCertificate(event.content().getReferenceId(), event.content().getId());
                break;
            case UNDEPLOY:
                removeCertificate(event.content().getReferenceId(), event.content().getId());
                break;
        }
    }

    @Override
    public Maybe<CertificateProvider> getCertificateProvider(String certificateId) {
        return doGetCertificateProvider(certificateId, System.currentTimeMillis());
    }

    private Maybe<CertificateProvider> doGetCertificateProvider(String certificateId, long startTime) {
        CertificateProvider certificateProvider = certificateProviders.get(certificateId);

        if (certificateProvider != null) {
            return Maybe.just(certificateProvider);
        }

        // certificate can be missing as it can take sometime for the reporter events
        // to propagate across the cluster so if the next call comes
        // in quickly at a different node there is a possibility it isn't available yet.
        try {
            Certificate certificate = certificateService.findById(certificateId).blockingGet();
            if (certificate == null) {
                return Maybe.empty();
            }
            // retry
            while (certificateProvider == null && System.currentTimeMillis() - startTime < retryTimeout) {
                certificateProvider = certificateProviders.get(certificateId);
            }
            return certificateProvider == null ? Maybe.empty() : Maybe.just(certificateProvider);
        } catch (Exception ex) {
            logger.error("An error has occurred while fetching certificate with id {}", certificateId, ex);
            throw new IllegalStateException(ex);
        }
    }

    private void deployCertificate(String certificateId) {
        logger.info("Management API has received a deploy certificate event for {}", certificateId);
        certificateService.findById(certificateId)
                .subscribe(
                        certificate -> loadCertificate(certificate),
                        error -> logger.error("Unable to deploy certificate {}", certificateId, error),
                        () -> logger.error("No certificate found with id {}", certificateId));
    }

    private void updateCertificate(String domainId, String certificateId) {
        logger.info("Management API has received a deploy certificate event for {}", certificateId);
        notifierService.unregisterCertificateExpiration(domainId, certificateId);
        notifierService.deleteCertificateExpirationAcknowledgement(certificateId)
                .doOnError(err -> logger.warn("Unable to delete notification acknowledge for certificate {} due to: {}", certificateId, err.getMessage()))
                .subscribe();
        deployCertificate(certificateId);
    }

    private void removeCertificate(String domainId, String certificateId) {
        logger.info("Management API has received a undeploy certificate event for {}", certificateId);
        certificateProviders.remove(certificateId);
        notifierService.unregisterCertificateExpiration(domainId, certificateId);
        notifierService.deleteCertificateExpirationAcknowledgement(certificateId)
                .doOnError(err -> logger.warn("Unable to delete notification acknowledge for certificate {} due to: {}", certificateId, err.getMessage()))
                .subscribe();
    }

    private void loadCertificate(Certificate certificate) {
        try {
            var providerConfig = new CertificateProviderConfiguration(certificate);
            var certificateProvider = certificatePluginManager.create(providerConfig);
            if (certificateProvider != null) {
                certificateProviders.put(certificate.getId(), certificateProvider);
                // expiration date is extracted from the Certificate by the provider
                // we update the certificate definition only if the expiration date has changed
                final Optional<Date> expirationDate = certificateProvider.getExpirationDate();
                expirationDate.ifPresent(expiresAt -> {
                    // set the value into the object to be sure that the info will be present during
                    // the notification registration
                    if (certificate.getExpiresAt() == null || !certificate.getExpiresAt().equals(expiresAt)) {
                        certificateService.updateExpirationDate(certificate.getId(), expiresAt)
                                .doOnError(err -> logger.warn("Unable to update expiration date for certificate {} due to: {}", certificate.getId(), err.getMessage()))
                                .subscribe();
                    }
                    certificate.setExpiresAt(expiresAt);
                });
                notifierService.registerCertificateExpiration(certificate);
            } else {
                notifierService.unregisterCertificateExpiration(certificate.getDomain(), certificate.getId());
                notifierService.deleteCertificateExpirationAcknowledgement(certificate.getId())
                        .doOnError(err -> logger.warn("Unable to delete notification acknowledge for certificate {} due to: {}", certificate.getId(), err.getMessage()))
                        .subscribe();
                certificateProviders.remove(certificate.getId());
            }
        } catch (Exception ex) {
            logger.error("An error has occurred while loading certificate: {} [{}]", certificate.getName(), certificate.getType(), ex);
            certificateProviders.remove(certificate.getId());
        }
    }
}
