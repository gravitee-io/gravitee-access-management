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
package io.gravitee.am.repository.gateway.api;

import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Reference;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface EmailStagingRepository {

    /**
     * Insert a new email staging entry
     * @param emailStaging the email staging to create
     * @return the created email staging
     */
    Single<EmailStaging> create(EmailStaging emailStaging);

    /**
     * Delete an email staging entry by its ID
     * @param id the email staging identifier
     * @return completable
     */
    Completable delete(String id);

    /**
     * Delete multiple email staging entries by their IDs
     * @param ids the list of email staging identifiers
     * @return completable
     */
    Completable delete(List<String> ids);

    /**
     * List email staging entries sorted by update date (oldest first) with a limit
     * @param limit the maximum number of entries to return
     * @return flowable of email staging entries
     */
    Flowable<EmailStaging> findOldestByUpdateDate(Reference reference, int limit);

    /**
     * Update the number of attempts for an email staging entry
     * @param id the email staging identifier
     * @param attempts the new number of attempts
     * @return the updated email staging
     */
    Single<EmailStaging> updateAttempts(String id, int attempts);
}
