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

import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.repository.management.api.LoginAttemptRepository;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class LoginAttemptRepositoryProxy extends AbstractProxy<LoginAttemptRepository> implements LoginAttemptRepository {

    @Override
    public Maybe<LoginAttempt> findByCriteria(LoginAttemptCriteria criteria) {
        return target.findByCriteria(criteria);
    }

    @Override
    public Completable delete(LoginAttemptCriteria criteria) {
        return target.delete(criteria);
    }

    @Override
    public Maybe<LoginAttempt> findById(String id) {
        return target.findById(id);
    }

    @Override
    public Single<LoginAttempt> create(LoginAttempt item) {
        return target.create(item);
    }

    @Override
    public Single<LoginAttempt> update(LoginAttempt item) {
        return target.update(item);
    }

    @Override
    public Completable delete(String id) {
        return target.delete(id);
    }
}
