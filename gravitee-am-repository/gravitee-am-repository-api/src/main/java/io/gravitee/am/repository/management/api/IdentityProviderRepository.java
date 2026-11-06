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

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.encryption.KeepEncryptedOnFailure;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IdentityProviderRepository extends CrudRepository<IdentityProvider, String> {

    Flowable<IdentityProvider> findAll(ReferenceType referenceType, String referenceId);

    /**
     * Same identity providers as {@link #findAll(ReferenceType, String)}, in a single query, but one whose configuration
     * cannot be decrypted is returned with its configuration still encrypted ({@code FieldEncryptor.isEncrypted}) instead of failing the whole list.
     * The configuration must not be handed to a plugin.
     */
    @KeepEncryptedOnFailure
    default Flowable<IdentityProvider> findAllForListing(ReferenceType referenceType, String referenceId) {
        return findAll(referenceType, referenceId);
    }

    Flowable<IdentityProvider> findAll(ReferenceType referenceType);

    Flowable<IdentityProvider> findAll();

    Maybe<IdentityProvider> findById(ReferenceType referenceType, String referenceId, String identityProviderId);

    Flowable<IdentityProvider> findAllByPasswordPolicy(ReferenceType referenceType, String referenceId, String passwordPolicy);

    /**
     * @return the ids of the identity providers of a reference, whose configuration is not decrypted
     */
    Flowable<String> findIdsByReference(Reference reference);
}
