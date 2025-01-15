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

import io.gravitee.am.model.Group;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.model.NewGroup;
import io.gravitee.am.service.model.UpdateGroup;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface OrganizationGroupService {

    Single<Page<Group>> findAll(String organizationId, int page, int size);

    Single<Page<User>> findMembers(String organizationId, String groupId, int page, int size);

    Flowable<Group> findAll(String organizationId);

    Flowable<Group> findByMember(String userId);

    Flowable<Group> findByIdIn(List<String> ids);

    Single<Group> findById(String organizationId, String id);

    Maybe<Group> findById(String id);

    Single<Group> create(String organizationId, NewGroup newGroup, io.gravitee.am.identityprovider.api.User principal);

    Single<Group> update(String organizationId, String id, UpdateGroup updateGroup, io.gravitee.am.identityprovider.api.User principal);

    Completable delete(String organizationId, String groupId, io.gravitee.am.identityprovider.api.User principal);

    default Completable delete(String organizationId, String groupId) {
        return delete(organizationId, groupId, null);
    }
}
