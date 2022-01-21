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
import io.gravitee.am.model.Factor;
import io.gravitee.am.service.model.NewFactor;
import io.gravitee.am.service.model.UpdateFactor;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface FactorService {

    Maybe<Factor> findById(String id);

    Flowable<Factor> findByDomain(String domain);

    Completable deleteByDomain(String domain);

    Single<Factor> create(String domain, NewFactor factor, User principal);

    Single<Factor> update(String domain, String id, UpdateFactor updateFactor, User principal);

    Completable delete(String domain, String factorId, User principal);

    default Single<Factor> create(String domain, NewFactor factor) {
        return create(domain, factor, null);
    }

    default Single<Factor> update(String domain, String id, UpdateFactor updateFactor) {
        return update(domain, id, updateFactor, null);
    }

    default Completable delete(String domain, String factorId) {
        return delete(domain, factorId, null);
    }
}
