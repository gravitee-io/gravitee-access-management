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

import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ExtensionGrantRepositoryProxy extends AbstractProxy<ExtensionGrantRepository> implements ExtensionGrantRepository {

    public Single<Set<ExtensionGrant>> findByDomain(String domain) {
        return target.findByDomain(domain);
    }

    @Override
    public Maybe<ExtensionGrant> findById(String id) {
        return target.findById(id);
    }

    @Override
    public Maybe<ExtensionGrant> findByDomainAndName(String domain, String name) {
        return target.findByDomainAndName(domain, name);
    }

    @Override
    public Single<ExtensionGrant> create(ExtensionGrant extensionGrant) {
        return target.create(extensionGrant);
    }

    @Override
    public Single<ExtensionGrant> update(ExtensionGrant extensionGrant) {
        return target.update(extensionGrant);
    }

    @Override
    public Completable delete(String id) {
        return target.delete(id);
    }
}
