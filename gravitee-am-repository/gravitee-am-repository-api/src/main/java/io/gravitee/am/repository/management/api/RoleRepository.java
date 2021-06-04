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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.common.CrudRepository;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface RoleRepository extends CrudRepository<Role, String> {

    Flowable<Role> findAll(ReferenceType referenceType, String referenceId);

    Single<Page<Role>> findAll(ReferenceType referenceType, String referenceId, int page, int size);

    Single<Page<Role>> search(ReferenceType referenceType, String referenceId, String query, int page, int size);

    Flowable<Role> findByIdIn(List<String> ids);

    Maybe<Role> findById(ReferenceType referenceType, String referenceId, String role);

    Maybe<Role> findByNameAndAssignableType(ReferenceType referenceType, String referenceId, String name, ReferenceType assignableType);

    Flowable<Role> findByNamesAndAssignableType(ReferenceType referenceType, String referenceId, List<String> name, ReferenceType assignableType);
}
