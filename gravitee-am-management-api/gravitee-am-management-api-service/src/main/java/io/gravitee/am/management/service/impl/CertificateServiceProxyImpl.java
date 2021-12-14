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
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import io.gravitee.am.management.service.CertificateServiceProxy;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.exception.CertificateNotFoundException;
import io.gravitee.am.service.exception.CertificatePluginSchemaNotFoundException;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.am.service.model.UpdateCertificate;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
        return certificateService.create(domain).flatMap(this::filterSensitiveData);
    }

    @Override
    public Single<Certificate> create(String domain, NewCertificate newCertificate, User principal) {
        return certificateService.create(domain, newCertificate, principal).flatMap(this::filterSensitiveData);
    }

    @Override
    public Single<Certificate> update(String domain, String id, UpdateCertificate updateCertificate, User principal) {
        return certificateService.findById(id)
                .switchIfEmpty(Single.error(new CertificateNotFoundException(id)))
                .flatMap(oldCertificate -> updateSensitiveData(updateCertificate, oldCertificate))
                .flatMap(certificateToUpdate -> certificateService.update(domain, id, certificateToUpdate, principal))
                .flatMap(this::filterSensitiveData);
    }

    @Override
    public Completable delete(String certificateId, User principal) {
        return certificateService.delete(certificateId, principal);
    }

    private Single<Certificate> filterSensitiveData(Certificate idp) {
        return certificatePluginService.getSchema(idp.getType())
                .switchIfEmpty(Single.error(new CertificatePluginSchemaNotFoundException(idp.getType())))
                .map(schema -> {
                    var schemaNode = objectMapper.readTree(schema);
                    var configurationNode = objectMapper.readTree(idp.getConfiguration());
                    super.filterSensitiveData(schemaNode, configurationNode, idp::setConfiguration);
                    return idp;
                });
    }

    private Single<UpdateCertificate> updateSensitiveData(UpdateCertificate updateCertificate, Certificate oldCertificate) {
        return certificatePluginService.getSchema(oldCertificate.getType())
                .switchIfEmpty(Single.error(new CertificatePluginSchemaNotFoundException(oldCertificate.getType())))
                .map(schema -> {
                    var updateConfig = objectMapper.readTree(updateCertificate.getConfiguration());
                    var oldConfig = objectMapper.readTree(oldCertificate.getConfiguration());
                    var schemaConfig = objectMapper.readTree(schema);
                    super.updateSensitiveData(updateConfig, oldConfig, schemaConfig, updateCertificate::setConfiguration);
                    return updateCertificate;
                });
    }
}
