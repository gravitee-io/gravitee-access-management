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
import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.model.UpdateReporter;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ReporterService {

    Single<List<Reporter>> findAll();

    Single<List<Reporter>> findByDomain(String domain);

    Maybe<Reporter> findById(String id);

    Single<Reporter> createDefault(String domain);

    Single<Reporter> create(String domain, NewReporter newReporter, User principal);

    Single<Reporter> update(String domain, String id, UpdateReporter updateReporter, User principal);

    Completable delete(String reporterId, User principal);

    default Single<Reporter> create(String domain, NewReporter newReporter) {
        return create(domain, newReporter, null);
    }

    default Single<Reporter> update(String domain, String id, UpdateReporter updateReporter) {
        return update(domain, id, updateReporter, null);
    }

    default Completable delete(String reporterId) {
        return delete(reporterId, null);
    }
}
