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
import io.gravitee.am.model.ReferenceType;
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
    Single<Page<Group>> findAll(ReferenceType referenceType, String referenceId, int page, int size);

    Single<Page<Group>> findByDomain(String domain, int page, int size);

    Single<Page<User>> findMembers(ReferenceType referenceType, String referenceId, String groupId, int page, int size);

    Single<List<Group>> findAll(ReferenceType referenceType, String referenceId);

    Single<List<Group>> findByDomain(String domain);

    Single<List<Group>> findByMember(String userId);

    Single<List<Group>> findByIdIn(List<String> ids);

    Maybe<Group> findByName(ReferenceType referenceType, String referenceId, String groupName);

    Maybe<Group> findByDomainAndName(String domain, String groupName);

    Single<Group> findById(ReferenceType referenceType, String referenceId, String id);

    Maybe<Group> findById(String id);

    Single<Group> create(
        ReferenceType referenceType,
        String referenceId,
        NewGroup newGroup,
        io.gravitee.am.identityprovider.api.User principal
    );

    Single<Group> create(String domain, NewGroup group, io.gravitee.am.identityprovider.api.User principal);

    Single<Group> update(
        ReferenceType referenceType,
        String referenceId,
        String id,
        UpdateGroup updateGroup,
        io.gravitee.am.identityprovider.api.User principal
    );

    Single<Group> update(String domain, String id, UpdateGroup group, io.gravitee.am.identityprovider.api.User principal);

    Completable delete(ReferenceType referenceType, String referenceId, String groupId, io.gravitee.am.identityprovider.api.User principal);

    Single<Group> assignRoles(
        ReferenceType referenceType,
        String referenceId,
        String groupId,
        List<String> roles,
        io.gravitee.am.identityprovider.api.User principal
    );

    Single<Group> revokeRoles(
        ReferenceType referenceType,
        String referenceId,
        String groupId,
        List<String> roles,
        io.gravitee.am.identityprovider.api.User principal
    );

    default Single<Group> create(String domain, NewGroup group) {
        return create(domain, group, null);
    }

    default Single<Group> update(String domain, String id, UpdateGroup group) {
        return update(domain, id, group, null);
    }

    default Completable delete(ReferenceType referenceType, String referenceId, String groupId) {
        return delete(referenceType, referenceId, groupId, null);
    }

    default Single<Group> assignRoles(ReferenceType referenceType, String referenceId, String groupId, List<String> roles) {
        return assignRoles(referenceType, referenceId, groupId, roles, null);
    }

    default Single<Group> revokeRoles(ReferenceType referenceType, String referenceId, String groupId, List<String> roles) {
        return revokeRoles(referenceType, referenceId, groupId, roles, null);
    }
}
