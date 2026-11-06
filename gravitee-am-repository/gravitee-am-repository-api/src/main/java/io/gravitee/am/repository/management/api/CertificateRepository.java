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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Certificate;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.encryption.KeepEncryptedOnFailure;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;

import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface CertificateRepository extends CrudRepository<Certificate, String> {

    Flowable<Certificate> findAll();

    Flowable<Certificate> findByDomain(String domain);

    /**
     * Same certificates as {@link #findByDomain(String)}, in a single query, but one whose configuration cannot be
     * decrypted is returned with its configuration still encrypted ({@code FieldEncryptor.isEncrypted}) instead of failing the whole list.
     * The configuration must not be handed to a plugin.
     */
    @KeepEncryptedOnFailure
    default Flowable<Certificate> findByDomainForListing(String domain) {
        return findByDomain(domain);
    }

    Completable updateExpirationDate(String  certificateId, Date expiresAt);

    /**
     * @return the ids of the certificates of a domain, whose configuration is not decrypted
     */
    Flowable<String> findIdsByDomain(String domain);
}
