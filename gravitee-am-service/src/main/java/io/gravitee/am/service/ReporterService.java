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

import io.gravitee.am.common.event.Action;
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

    /**
     * Deletes the reporter specified by {@code reporterId}.
     *
     * @param reporterId the ID of the reporter to delete
     * @param principal the user requesting the deletion
     * @param removeSystemReporter if true then remove system(default) reporter
     * @return a {@link Completable} that completes if deletion is successful or emits an error
     */
    Completable delete(String reporterId, User principal, boolean removeSystemReporter);

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

    /**
     * Deletes the reporter specified by {@code reporterId}. Includes system reporter.
     *
     * @param reporterId the ID of the reporter to delete
     * @return a {@link Completable} that completes if deletion is successful or emits an error
     */
    default Completable delete(String reporterId) {
        return delete(reporterId, null, true);
    }

    /**
     * Deletes the reporter specified by {@code reporterId}. Not includes system reporter. Restricted to API calls.
     * Calling this with system reporter ID will return error.
     *
     * @param reporterId the ID of the reporter to delete
     * @return a {@link Completable} that completes if deletion is successful or emits an error
     */
    default Completable delete(String reporterId, User principal) {
        return delete(reporterId, principal, false);
    }

    Completable notifyInheritedReporters(Reference parentReference, Reference affectedReference, Action action);
}
