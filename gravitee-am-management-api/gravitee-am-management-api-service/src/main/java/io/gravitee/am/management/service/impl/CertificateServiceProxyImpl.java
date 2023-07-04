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
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.exception.CertificatePluginSchemaNotFoundException;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.am.service.model.UpdateCertificate;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.CertificateAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class CertificateServiceProxyImpl extends AbstractSensitiveProxy implements CertificateServiceProxy {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificatePluginService certificatePluginService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Maybe<Certificate> findById(String id) {
        return certificateService.findById(id).flatMap(reporter -> filterSensitiveData(reporter).toMaybe());
    }

    @Override
    public Flowable<Certificate> findAll() {
        return certificateService.findAll().flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Flowable<Certificate> findByDomain(String domain) {
        return certificateService.findByDomain(domain).flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Single<Certificate> create(String domain) {
        return certificateService.create(domain)
                .flatMap(this::filterSensitiveData)
                .doOnSuccess(certificate -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class).type(EventType.CERTIFICATE_CREATED).certificate(certificate)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class).type(EventType.CERTIFICATE_CREATED).throwable(throwable)));
    }

    @Override
    public Single<Certificate> create(String domain, NewCertificate newCertificate, User principal, boolean isSystem) {
        return certificateService.create(domain, newCertificate, principal, isSystem)
                .flatMap(this::filterSensitiveData)
                .doOnSuccess(certificate -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class).principal(principal).type(EventType.CERTIFICATE_CREATED).certificate(certificate)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class).principal(principal).type(EventType.CERTIFICATE_CREATED).throwable(throwable)));
    }

    @Override
    public Single<Certificate> update(String domain, String id, UpdateCertificate updateCertificate, User principal) {
        return certificateService.findById(id)
                .switchIfEmpty(Single.error(() -> new CertificateNotFoundException(id)))
                .flatMap(oldCertificate -> filterSensitiveData(oldCertificate)
                        .flatMap(safeOldCert -> updateSensitiveData(updateCertificate, oldCertificate)
                                .flatMap(certificateToUpdate -> certificateService.update(domain, id, certificateToUpdate, principal))
                                .flatMap(this::filterSensitiveData)
                                .doOnSuccess(certificate -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class).principal(principal).type(EventType.CERTIFICATE_UPDATED).oldValue(safeOldCert).certificate(certificate)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(CertificateAuditBuilder.class).principal(principal).type(EventType.CERTIFICATE_UPDATED).throwable(throwable))))
                );
    }

    @Override
    public Completable updateExpirationDate(String certificateId, Date expirationDate) {
        return this.certificateService.updateExpirationDate(certificateId, expirationDate);
    }

    @Override
    public Completable delete(String certificateId, User principal) {
        return certificateService.delete(certificateId, principal);
    }

    @Override
    public Single<Certificate> rotate(String domain, User principal) {
        return certificateService.rotate(domain, principal);
    }

    private Single<Certificate> filterSensitiveData(Certificate cert) {
        return certificatePluginService.getSchema(cert.getType())
                .switchIfEmpty(Single.error(() -> new CertificatePluginSchemaNotFoundException(cert.getType())))
                .map(schema -> {
                    // Duplicate the object to avoid side effect
                    var filteredEntity = new Certificate(cert);
                    var schemaNode = objectMapper.readTree(schema);
                    var configurationNode = objectMapper.readTree(filteredEntity.getConfiguration());
                    super.filterSensitiveData(schemaNode, configurationNode, filteredEntity::setConfiguration);
                    return filteredEntity;
                });
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
