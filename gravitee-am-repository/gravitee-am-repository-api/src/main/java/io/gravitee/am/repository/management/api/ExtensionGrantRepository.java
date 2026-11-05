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

import io.reactivex.rxjava3.core.Completable;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.encryption.KeepEncryptedOnFailure;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ExtensionGrantRepository extends CrudRepository<ExtensionGrant, String> {

    Flowable<ExtensionGrant> findAll();

    Flowable<ExtensionGrant> findByDomain(String domain);

    /**
     * Same extension grants as {@link #findByDomain(String)}, in a single query, but one whose configuration cannot be
     * decrypted is returned with its configuration still encrypted ({@code FieldEncryptor.isEncrypted}) instead of failing the whole list.
     * The configuration must not be handed to a plugin.
     */
    @KeepEncryptedOnFailure
    default Flowable<ExtensionGrant> findByDomainForListing(String domain) {
        return findByDomain(domain);
    }

    Maybe<ExtensionGrant> findByDomainAndName(String domain, String name);

    /**
     * Deletes the extension grants of a domain without reading them, so that a configuration which cannot be decrypted does not prevent it.
     */
    Completable deleteByDomain(String domain);
}
