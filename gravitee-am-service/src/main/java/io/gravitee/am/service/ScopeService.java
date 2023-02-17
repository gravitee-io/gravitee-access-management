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
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.service.model.*;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface ScopeService {

    Maybe<Scope> findById(String id);

    Single<Scope> create(String domain, NewScope scope, User principal);

    Single<Scope> create(String domain, NewSystemScope scope);

    Single<Page<Scope>> findByDomain(String domain, int page, int size);

    Maybe<Scope> findByDomainAndKey(String domain, String scopeKey);

    Single<List<Scope>> findByDomainAndKeys(String domain, List<String> scopeKeys);

    Single<Scope> patch(String domain, String id, PatchScope patchScope, User principal);

    Single<Scope> update(String domain, String id, UpdateScope updateScope, User principal);

    Single<Scope> update(String domain, String id, UpdateSystemScope updateScope);

    Completable delete(String scopeId, boolean force, User principal);

    Single<Page<Scope>> search(String domain, String query, int page, int size);

    /**
     * Throw InvalidClientMetadataException if null or empty, or contains unknown scope.
     * @param scopes Array of scope to validate.
     */
    Single<Boolean> validateScope(String domain, List<String> scopes);

    default Single<Scope> create(String domain, NewScope scope) {
        return create(domain, scope, null);
    }

    default Single<Scope> patch(String domain, String id, PatchScope patchScope) {
        return patch(domain, id, patchScope, null);
    }

    default Single<Scope> update(String domain, String id, UpdateScope updateScope) {
        return update(domain, id, updateScope, null);
    }

    default Completable delete(String scopeId, boolean force) {
        return delete(scopeId, force, null);
    }
}
