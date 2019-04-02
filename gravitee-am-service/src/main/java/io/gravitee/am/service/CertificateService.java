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
package io.gravitee.am.service;

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.am.service.model.UpdateCertificate;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CertificateService {

    Maybe<Certificate> findById(String id);

    Single<List<Certificate>> findAll();

    Single<List<Certificate>> findByDomain(String domain);

    Single<Certificate> create(String domain, NewCertificate newCertificate, String schema, User principal);

    Single<Certificate> update(String domain, String id, UpdateCertificate updateCertificate, String schema, User principal);

    Single<Certificate> update(Certificate certificate);

    Completable delete(String certificateId, User principal);

    void setCertificateProviders(Map<String, CertificateProvider> certificateProviders);

    void setCertificateProvider(String certificateId, CertificateProvider certificateProvider);

    Maybe<CertificateProvider> getCertificateProvider(String certificateId);

    default Single<Certificate> create(String domain, NewCertificate newCertificate, String schema) {
        return create(domain, newCertificate, schema, null);
    }

    default Single<Certificate> update(String domain, String id, UpdateCertificate updateCertificate, String schema) {
        return update(domain, id, updateCertificate, schema, null);
    }

    default Completable delete(String certificateId) {
        return delete(certificateId, null);
    }

}
