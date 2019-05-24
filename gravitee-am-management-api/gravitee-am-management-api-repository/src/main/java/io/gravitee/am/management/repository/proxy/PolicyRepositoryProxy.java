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
package io.gravitee.am.management.repository.proxy;

import io.gravitee.am.model.Policy;
import io.gravitee.am.repository.management.api.PolicyRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class PolicyRepositoryProxy extends AbstractProxy<PolicyRepository> implements PolicyRepository {

    @Override
    public Single<List<Policy>> findAll() {
        return target.findAll();
    }

    @Override
    public Single<List<Policy>> findByDomain(String domain) {
        return target.findByDomain(domain);
    }

    @Override
    public Maybe<Policy> findById(String id) {
        return target.findById(id);
    }

    @Override
    public Single<Policy> create(Policy item) {
        return target.create(item);
    }

    @Override
    public Single<Policy> update(Policy item) {
        return target.update(item);
    }

    @Override
    public Completable delete(String id) {
        return target.delete(id);
    }
}
