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

import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DomainRepositoryProxy extends AbstractProxy<DomainRepository> implements DomainRepository {

    @Override
    public Single<Set<Domain>> findAll() {
        return target.findAll();
    }

    @Override
    public Single<Set<Domain>> findByIdIn(Collection<String> ids) {
        return target.findByIdIn(ids);
    }

    @Override
    public Maybe<Domain> findMaster() {
        return target.findMaster();
    }

    @Override
    public Maybe<Domain> findById(String id) {
        return target.findById(id);
    }

    @Override
    public Single<Domain> create(Domain domain) {
        return this.target.create(domain);
    }

    @Override
    public Single<Domain> update(Domain domain) {
        return target.update(domain);
    }

    @Override
    public Completable delete(String id) {
        return target.delete(id);
    }
}
