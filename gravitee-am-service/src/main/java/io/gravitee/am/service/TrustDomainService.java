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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.CrossAppAccessResourceServerView;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.gravitee.am.service.model.NewTrustDomain;
import io.gravitee.am.service.model.NewTrustedDomain;
import io.gravitee.am.service.model.UpdateTrustDomain;
import io.gravitee.am.service.model.UpdateTrustedDomain;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

public interface TrustDomainService {

    Maybe<TrustedDomain> findById(String id);

    /**
     * Looks up a trusted domain by the label it is known by, unique within a reference.
     */
    Maybe<TrustedDomain> findByName(ReferenceType referenceType, String referenceId, String name);

    /**
     * Looks up the trusted domain that vouches for a SPIFFE trust domain, unique within a reference.
     */
    Maybe<TrustedDomain> findBySpiffeTrustDomain(ReferenceType referenceType, String referenceId, String spiffeTrustDomain);

    Flowable<TrustedDomain> findByReference(ReferenceType referenceType, String referenceId);

    Flowable<CrossAppAccessResourceServerView> searchCrossAppAccessResourceServers(Reference reference, String query, int limit);

    Single<TrustedDomain> create(Domain domain, NewTrustedDomain newTrustedDomain, User principal);

    Single<TrustedDomain> update(Domain domain, String id, UpdateTrustedDomain updateTrustedDomain, User principal);

    @Deprecated
    Single<TrustedDomain> create(Domain domain, NewTrustDomain newTrustDomain, User principal);

    @Deprecated
    Single<TrustedDomain> update(Domain domain, String id, UpdateTrustDomain updateTrustDomain, User principal);

    Completable delete(Domain domain, String id, User principal);
}
