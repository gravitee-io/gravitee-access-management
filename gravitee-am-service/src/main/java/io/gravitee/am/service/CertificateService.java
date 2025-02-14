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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.model.NewCertificate;
import io.gravitee.am.service.model.UpdateCertificate;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CertificateService {

    Maybe<Certificate> findById(String id);

    Flowable<Certificate> findAll();

    Flowable<Certificate> findByDomain(String domain);

    /**
     * This method is used to create a default certificate (mainly used when creating a new domain).
     * @return
     */
    Single<Certificate> create(Domain domain);

    /**
     * Request the generation of a new system certificate for the given domain
     * @param domain
     * @return the new Certificate
     */
    Single<Certificate> rotate(Domain domain, User principal);

    default Single<Certificate> create(Domain domain, NewCertificate newCertificate, User principal) {
        return this.create(domain, newCertificate, principal, false);
    }

    /**
     * This method is used to create a new certificate. If the isSystem parameter is set to true,
     * the certificate is a <i>Default</i> certificate generate during the domain creation
     * @param domain
     * @param newCertificate
     * @param principal
     * @param isSystem
     * @return
     */
    Single<Certificate> create(Domain domain, NewCertificate newCertificate, User principal, boolean isSystem);

    Single<Certificate> update(Domain domain, String id, UpdateCertificate updateCertificate, User principal);

    Completable delete(String certificateId, User principal);

    Completable updateExpirationDate(String certificateId, Date expirationDate);

    default Single<Certificate> create(Domain domain, NewCertificate newCertificate, boolean isSystem) {
        return create(domain, newCertificate, null, isSystem);
    }

    default Single<Certificate> update(Domain domain, String id, UpdateCertificate updateCertificate) {
        return update(domain, id, updateCertificate, null);
    }

    default Completable delete(String certificateId) {
        return delete(certificateId, null);
    }

}
