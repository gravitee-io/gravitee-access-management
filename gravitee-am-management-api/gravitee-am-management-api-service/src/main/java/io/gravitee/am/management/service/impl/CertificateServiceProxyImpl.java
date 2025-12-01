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
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import io.gravitee.am.management.service.CertificateServiceProxy;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reference;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.exception.CertificatePluginSchemaNotFoundException;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.am.service.model.UpdateCertificate;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.CertificateAuditBuilder;
import io.gravitee.am.service.utils.CertificateTimeComparator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CertificateServiceProxyImpl extends AbstractSensitiveProxy implements CertificateServiceProxy {

    private final CertificateService certificateService;
    private final IdentityProviderService idps;
    private final ApplicationService apps;
    private final CertificatePluginService certificatePluginService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private final Duration certExpirationWarningThreshold;

    public CertificateServiceProxyImpl(CertificateService certificateService,
                                       IdentityProviderService idps,
                                       ApplicationService apps,
                                       CertificatePluginService certificatePluginService,
                                       AuditService auditService,
                                       ObjectMapper objectMapper,
                                       @Qualifier("certificateNotifierSettings") NotifierSettings certificateNotifierSettings) {
        this.certificateService = certificateService;
        this.idps = idps;
        this.apps = apps;
        this.certificatePluginService = certificatePluginService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.certExpirationWarningThreshold = Duration.ofDays(certificateNotifierSettings.expiryThresholds().get(0));
    }


    @Override
    public Maybe<Certificate> findById(String id) {
        return certificateService.findById(id).flatMap(reporter -> filterSensitiveData(reporter).toMaybe());
    }

    @Override
    public Single<List<CertificateEntity>> findByDomainAndUse(String domainId, String use) {
        return certificateService.findByDomain(domainId)
                .filter(cert -> {
                    if (StringUtils.isBlank(use)) {
                        return true;
                    }
                    return cert.hasUse(use, objectMapper::readTree);
                })
                .flatMapSingle(this::filterSensitiveData)
                .toList()
                .map(allCerts -> {
                    var renewedSystemCertIds = allCerts
                            .stream()
                            .filter(Certificate::isSystem)
                            .sorted(new CertificateTimeComparator())
                            // all system certs are renewed - except for the newest one
                            .skip(1)
                            .map(Certificate::getId)
                            .collect(Collectors.toSet());
                    return allCerts
                            .stream()
                            .map(c -> Pair.of(c, renewedSystemCertIds.contains(c.getId())))
                            .toList();
                })
                .flattenAsFlowable(certsAndIds -> certsAndIds)
                .flatMapSingle(pair -> {
                    var cert = pair.getLeft();
                    var isRenewedSystemCert = pair.getRight();
                    return Single.zip(
                            getAppsUsing(cert).toList(),
                            getIdpsUsing(cert).toList(),
                            (appUsage, idpUsage) -> CertificateEntity.forList(cert, certExpirationWarningThreshold, isRenewedSystemCert, appUsage, idpUsage)
                    );
                })
                .sorted(Comparator.comparing(CertificateEntity::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Flowable<IdentityProvider> getIdpsUsing(Certificate cert) {
        return idps.findByCertificate(Reference.domain(cert.getDomain()), cert.getId())
                .map(idp -> {
                    final var idpId = new IdentityProvider();
                    idpId.setId(idpId.getId());
                    idpId.setName(idp.getName());
                    return idpId;
                });
    }

    private Flowable<Application> getAppsUsing(Certificate cert) {
        return apps.findByCertificate(cert.getId())
                .map(app -> {
                    final var appId = new Application();
                    appId.setId(app.getId());
                    appId.setName(app.getName());
                    return appId;
                });
    }

    @Override
    public Single<Certificate> create(Domain domain, NewCertificate newCertificate, User principal) {
        return certificateService.create(domain, newCertificate, principal, false)
                .flatMap(this::filterSensitiveData)
                .doOnSuccess(certificate -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.CERTIFICATE_CREATED)
                        .certificate(certificate)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.CERTIFICATE_CREATED)
                        .reference(Reference.domain(domain.getId()))
                        .throwable(throwable)));
    }

    @Override
    public Single<Certificate> update(Domain domain, String id, UpdateCertificate updateCertificate, User principal) {

        Supplier<CertificateAuditBuilder> audit = () ->
                AuditBuilder.builder(CertificateAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.CERTIFICATE_UPDATED)
                        .reference(Reference.domain(domain.getId()));

        return certificateService.findById(id)
                .switchIfEmpty(Single.error(() -> new CertificateNotFoundException(id)))
                .doOnError(err -> auditService.report(audit.get().throwable(err)))
                .flatMap(oldCertificate -> filterSensitiveData(oldCertificate)
                        .doOnError(err -> auditService.report(audit.get().throwable(err)))
                        .flatMap(safeOldCert -> updateSensitiveData(updateCertificate, oldCertificate)
                                .flatMap(certificateToUpdate -> certificateService.update(domain, id, certificateToUpdate, principal))
                                .flatMap(this::filterSensitiveData)
                                .doOnSuccess(updated -> auditService.report(audit.get().oldValue(safeOldCert).certificate(updated)))
                                .doOnError(err -> auditService.report(audit.get().oldValue(safeOldCert).throwable(err)))
                        )
                );
    }


    @Override
    public Completable delete(String certificateId, User principal) {
        return certificateService.delete(certificateId, principal);
    }

    @Override
    public Single<Certificate> rotate(Domain domain, User principal) {
        return certificateService.rotate(domain, principal);
    }

    private Single<Certificate> filterSensitiveData(Certificate cert) {
        return certificatePluginService.getSchema(cert.getType())
                .map(schema -> {
                    // Duplicate the object to avoid side effect
                    var filteredEntity = new Certificate(cert);
                    var schemaNode = objectMapper.readTree(schema);
                    var configurationNode = objectMapper.readTree(filteredEntity.getConfiguration());
                    super.filterSensitiveData(schemaNode, configurationNode, filteredEntity::setConfiguration);
                    return filteredEntity;
                })
                .switchIfEmpty(Single.fromSupplier(() -> {
                    Certificate stub = new Certificate(cert);
                    stub.setConfiguration("{}");
                    return stub;
                }));
    }

    private Single<UpdateCertificate> updateSensitiveData(UpdateCertificate updateCertificate, Certificate oldCertificate) {
        return certificatePluginService.getSchema(oldCertificate.getType())
                .switchIfEmpty(Single.error(() -> new CertificatePluginSchemaNotFoundException(oldCertificate.getType())))
                .map(schema -> {
                    var updateConfig = objectMapper.readTree(updateCertificate.getConfiguration());
                    var oldConfig = objectMapper.readTree(oldCertificate.getConfiguration());
                    var schemaConfig = objectMapper.readTree(schema);
                    super.updateSensitiveData(updateConfig, oldConfig, schemaConfig, updateCertificate::setConfiguration);
                    return updateCertificate;
                });
    }
}
