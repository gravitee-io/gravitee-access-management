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
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IdentityProviderService {

    Single<List<IdentityProvider>> findAll();

    Single<IdentityProvider> findById(ReferenceType referenceType, String referenceId, String id);

    Maybe<IdentityProvider> findById(String id);

    Single<List<IdentityProvider>> findAll(ReferenceType referenceType, String referenceId);

    Flowable<IdentityProvider> findAll(ReferenceType referenceType);

    Single<List<IdentityProvider>> findByDomain(String domain);

    Single<IdentityProvider> create(ReferenceType referenceType, String referenceId, NewIdentityProvider newIdentityProvider, User principal);

    Single<IdentityProvider> create(String domain, NewIdentityProvider identityProvider, User principal);

    Single<IdentityProvider> update(ReferenceType referenceType, String referenceId, String id, UpdateIdentityProvider updateIdentityProvider, User principal);

    Single<IdentityProvider> update(String domain, String id, UpdateIdentityProvider updateIdentityProvider, User principal);

    Completable delete(ReferenceType referenceType, String referenceId, String identityProviderId, User principal);

    Completable delete(String domain, String identityProviderId, User principal);

    default Single<IdentityProvider> create(String domain, NewIdentityProvider identityProvider) {
        return create(domain, identityProvider, null);
    }

    default Single<IdentityProvider> update(String domain, String id, UpdateIdentityProvider updateIdentityProvider) {
        return update(domain, id, updateIdentityProvider, null);
    }

    default Completable delete(String domain, String identityProviderId) {
        return delete(domain, identityProviderId, null);
    }
}
