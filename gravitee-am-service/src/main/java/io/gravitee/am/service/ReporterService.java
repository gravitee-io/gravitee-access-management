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
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.model.UpdateReporter;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ReporterService {

    Flowable<Reporter> findAll();


    Flowable<Reporter> findByReference(Reference reference);


    Maybe<Reporter> findById(String id);

    Single<Reporter> createDefault(Reference reference);

    NewReporter createInternal(Reference reference);

    Single<Reporter> create(Reference reference, NewReporter newReporter, User principal, boolean system);

    Single<Reporter> update(Reference reference, String id, UpdateReporter updateReporter, User principal, boolean isUpgrader);

    Completable delete(String reporterId, User principal);

    String createReporterConfig(Reference reference);

    default NewReporter createInternal() {
        return createInternal(new Reference(ReferenceType.PLATFORM, Platform.DEFAULT));
    }

    default Single<Reporter> create(Reference reference, NewReporter newReporter) {
        return create(reference, newReporter, null, true);
    }

    default Single<Reporter> update(Reference reference, String id, UpdateReporter updateReporter, boolean isUpgrader) {
        return update(reference, id, updateReporter, null, isUpgrader);
    }

    default Completable delete(String reporterId) {
        return delete(reporterId, null);
    }

}
