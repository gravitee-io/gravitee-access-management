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

import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.UserRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UserRepositoryProxy extends AbstractProxy<UserRepository> implements UserRepository {

    @Override
    public Single<Set<User>> findByDomain(String domain) {
        return target.findByDomain(domain);
    }

    @Override
    public Single<Page<User>> findByDomain(String domain, int page, int size) {
        return target.findByDomain(domain, page, size);
    }

    @Override
    public Single<Page<User>> search(String domain, String query, int limit) {
        return target.search(domain, query, limit);
    }

    @Override
    public Single<List<User>> findByDomainAndEmail(String domain, String email) {
        return target.findByDomainAndEmail(domain, email);
    }

    @Override
    public Maybe<User> findByUsernameAndDomain(String username, String domain) {
        return target.findByUsernameAndDomain(username, domain);
    }

    @Override
    public Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source) {
        return target.findByDomainAndUsernameAndSource(domain, username, source);
    }

    @Override
    public Single<List<User>> findByIdIn(List<String> ids) {
        return target.findByIdIn(ids);
    }

    @Override
    public Maybe<User> findById(String id) {
        return target.findById(id);
    }

    @Override
    public Single<User> create(User item) {
        return target.create(item);
    }

    @Override
    public Single<User> update(User item) {
        return target.update(item);
    }

    @Override
    public Completable delete(String id) {
        return target.delete(id);
    }
}
