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
import io.gravitee.am.model.Role;
import io.gravitee.am.service.model.NewRole;
import io.gravitee.am.service.model.UpdateRole;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface RoleService {

    Single<Set<Role>> findByDomain(String domain);

    Maybe<Role> findById(String id);

    Single<Set<Role>> findByIdIn(List<String> ids);

    Single<Role> create(String domain, NewRole role, User principal);

    Single<Role> update(String domain, String id, UpdateRole role, User principal);

    Completable delete(String roleId, User principal);

    default Single<Role> create(String domain, NewRole role) {
        return create(domain, role, null);
    }

    default Single<Role> update(String domain, String id, UpdateRole role) {
        return update(domain, id, role, null);
    }

    default Completable delete(String roleId) {
        return delete(roleId, null);
    }

}
