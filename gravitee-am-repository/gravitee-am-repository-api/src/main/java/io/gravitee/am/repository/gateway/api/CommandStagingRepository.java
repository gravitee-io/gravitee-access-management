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

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.command.CommandStaging;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Staging store for OpenID Provider Command dispatch jobs.
 *
 * @author GraviteeSource Team
 */
public interface CommandStagingRepository {

    /**
     * Insert a new command staging entry keyed by the command id. All gateway nodes
     * of a domain race on this insert when they receive the sync event: the unique
     * key guarantees a single job.
     *
     * @param commandStaging the command staging entry to create, its id is required
     * @return the created entry, or empty when an entry with the same id already exists
     */
    Maybe<CommandStaging> createIfAbsent(CommandStaging commandStaging);

    /**
     * List command staging entries sorted by update date (oldest first) with a limit
     *
     * @param reference the domain reference
     * @param limit the maximum number of entries to return
     * @return flowable of command staging entries
     */
    Flowable<CommandStaging> findOldestByUpdateDate(Reference reference, int limit);

    /**
     * Update the delivery state (attempts and terminal client ids) of an entry
     *
     * @param commandStaging the entry to update
     * @return the updated entry
     */
    Single<CommandStaging> update(CommandStaging commandStaging);

    /**
     * Delete a command staging entry by its id
     *
     * @param id the command staging identifier
     * @return completable
     */
    Completable delete(String id);
}
