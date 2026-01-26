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
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ProtectedResource.Type;
import io.gravitee.am.model.ProtectedResourcePrimaryData;
import io.gravitee.am.model.ProtectedResourceSecret;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.PageSortRequest;
import io.gravitee.am.service.model.NewProtectedResource;
import io.gravitee.am.service.model.PatchProtectedResource;
import io.gravitee.am.service.model.UpdateProtectedResource;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

public interface ProtectedResourceService {

    Maybe<ProtectedResource> findById(String id);

    default Maybe<ProtectedResourcePrimaryData> findByDomainAndIdAndType(String domain, String id, ProtectedResource.Type type) {
        return findById(id)
                .filter(res -> res.getDomainId().equals(domain))
                .filter(res -> res.getType().equals(type))
                .map(ProtectedResourcePrimaryData::of);
    }

    Single<ProtectedResourceSecret> create(Domain domain, User user, NewProtectedResource protectedResource);

    Single<ProtectedResourcePrimaryData> update(Domain domain, String id, UpdateProtectedResource updateProtectedResource, User principal);

    Single<ProtectedResourcePrimaryData> patch(Domain domain, String id, PatchProtectedResource patchProtectedResource, User principal);

    Single<Page<ProtectedResourcePrimaryData>> findByDomainAndType(String domain, Type type, PageSortRequest pageSortRequest);

    Single<Page<ProtectedResourcePrimaryData>> findByDomainAndTypeAndIds(String domain, Type type, List<String> ids, PageSortRequest pageSortRequest);

    Single<Page<ProtectedResourcePrimaryData>> search(String domain, Type type, String query, PageSortRequest pageSortRequest);

    Completable delete(Domain domain, String id, Type expectedType, User principal);

    Flowable<ProtectedResource> findAll();

    Flowable<ProtectedResource> findByDomain(String domain);

    Flowable<ProtectedResource> findByCertificate(String certificateId);

    Single<ClientSecret> createSecret(Domain domain, String id, String name, User principal);

    Single<ClientSecret> renewSecret(Domain domain, String id, String secretId, User principal);

    Completable deleteSecret(Domain domain, String id, String secretId, User principal);
}
