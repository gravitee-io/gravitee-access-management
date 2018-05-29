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

import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.repository.management.api.ScopeRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ScopeRepositoryProxy extends AbstractProxy<ScopeRepository> implements ScopeRepository {

    public Single<Set<Scope>> findByDomain(String domain) {
        return target.findByDomain(domain);
    }

    public Maybe<Scope> findByDomainAndKey(String domain, String key) {
        return target.findByDomainAndKey(domain, key);
    }

    public Maybe<Scope> findById(String s) {
        return target.findById(s);
    }

    public Single<Scope> create(Scope item) {
        return target.create(item);
    }

    public Single<Scope> update(Scope item) {
        return target.update(item);
    }

    public Completable delete(String s) {
        return target.delete(s);
    }
}
