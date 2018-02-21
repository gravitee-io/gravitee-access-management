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

import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.UserRepository;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UserRepositoryProxy extends AbstractProxy<UserRepository> implements UserRepository {

    @Override
    public Single<Set<User>> findByDomain(String domain) throws TechnicalException {
        return target.findByDomain(domain);
    }

    @Override
    public Single<Page<User>> findByDomain(String domain, int page, int size) throws TechnicalException {
        return target.findByDomain(domain, page, size);
    }

    @Override
    public Maybe<User> findByUsernameAndDomain(String username, String domain) throws TechnicalException {
        return target.findByUsernameAndDomain(username, domain);
    }

    @Override
    public Maybe<User> findById(String id) throws TechnicalException {
        return target.findById(id);
    }

    @Override
    public Single<User> create(User item) throws TechnicalException {
        return target.create(item);
    }

    @Override
    public Single<User> update(User item) throws TechnicalException {
        return target.update(item);
    }

    @Override
    public Single<Irrelevant> delete(String id) throws TechnicalException {
        return target.delete(id);
    }
}
