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
import io.gravitee.am.model.Policy;
import io.gravitee.am.service.model.NewPolicy;
import io.gravitee.am.service.model.UpdatePolicy;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface PolicyService {

    Single<List<Policy>> findAll();

    Single<List<Policy>> findByDomain(String domain);

    Maybe<Policy> findById(String id);

    Single<Policy> create(String domain, NewPolicy newPolicy, User principal);

    Single<Policy> update(String domain, String id, UpdatePolicy updatePolicy, User principal);

    Single<List<Policy>> update(String domain, List<Policy> policies, User principal);

    Completable delete(String id, User principal);

    default Single<Policy> create(String domain, NewPolicy newPolicy) {
        return create(domain, newPolicy, null);
    }

    default Single<Policy> update(String domain, String id, UpdatePolicy updatePolicy) {
        return update(domain, id, updatePolicy, null);
    }

    default Single<List<Policy>> update(String domain, List<Policy> policies) {
        return update(domain, policies, null);
    }

    default Completable delete(String id) {
        return delete(id, null);
    }
}
