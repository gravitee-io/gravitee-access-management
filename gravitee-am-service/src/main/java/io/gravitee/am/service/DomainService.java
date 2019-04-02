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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.am.service.model.UpdateDomain;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.Collection;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DomainService {

    Maybe<Domain> findById(String id);

    Single<Set<Domain>> findAll();

    Single<Set<Domain>> findByIdIn(Collection<String> ids);

    Single<Domain> create(NewDomain domain, User principal);

    Single<Domain> update(String domainId, UpdateDomain domain, User principal);

    Single<Domain> reload(String domainId, Event event);

    Single<Domain> patch(String domainId, PatchDomain domain, User principal);

    Single<Domain> setMasterDomain(String domainId, boolean isMaster);

    Single<Domain> deleteLoginForm(String domainId);

    Completable delete(String domain, User principal);

    default Single<Domain> create(NewDomain domain) {
        return create(domain, null);
    }

    default Single<Domain> update(String domainId, UpdateDomain domain) {
        return update(domainId, domain, null);
    }

    default Single<Domain> patch(String domainId, PatchDomain domain) {
        return patch(domainId, domain, null);
    }

    default Completable delete(String domain) {
        return delete(domain, null);
    }
}
