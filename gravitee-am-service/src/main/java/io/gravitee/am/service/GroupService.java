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
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface GroupService {

    Single<Page<Group>> findByDomain(String domain, int page, int size);

    Single<List<Group>> findByDomain(String domain);

    Single<Page<User>> findMembers(String groupId, int page, int size);

    Maybe<Group> findByDomainAndName(String domain, String groupName);

    Maybe<Group> findById(String id);

    Single<Group> create(String domain, NewGroup group, io.gravitee.am.identityprovider.api.User principal);

    Single<Group> update(String domain, String id, UpdateGroup group, io.gravitee.am.identityprovider.api.User principal);

    Completable delete(String groupId, io.gravitee.am.identityprovider.api.User principal);

    default Single<Group> create(String domain, NewGroup group) {
        return create(domain, group, null);
    }

    default Single<Group> update(String domain, String id, UpdateGroup group) {
        return update(domain, id, group, null);
    }

    default Completable delete(String groupId) {
        return delete(groupId, null);
    }


}
