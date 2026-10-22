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
package io.gravitee.am.repository.mongodb.common;

import com.mongodb.reactivestreams.client.MongoDatabase;
import lombok.CustomLog;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author GraviteeSource Team
 */
@CustomLog
public final class MongoIndexUpgrade {

    public static final int ORDER = -1000;
    public static final String UPGRADE_MODE_PROPERTY = "upgrade.mode";

    private final MongoDatabase database;
    private final MongoIndexDeclarations declarations;
    private final boolean upgradeOnly;
    private final AtomicReference<String> failedChangeset = new AtomicReference<>();

    public MongoIndexUpgrade(MongoDatabase database, MongoIndexDeclarations declarations, boolean upgradeOnly) {
        this.database = database;
        this.declarations = declarations;
        this.upgradeOnly = upgradeOnly;
    }

    public List<MongoIndexDeclarations.Changeset> changesets() {
        return declarations.changesets();
    }

    public boolean apply(MongoIndexDeclarations.Changeset changeset) {
        final String failedBefore = failedChangeset.get();
        if (failedBefore != null) {
            return fail(changeset, "changeset " + failedBefore + " failed before it");
        }

        log.info("Applying Mongo index changeset {} of {} to database {}", changeset.id(), declarations.source(), database.getName());
        final List<MongoIndexReport.IndexOutcome> failures = MongoIndexManager.ensureDeclarations(database, changeset.indexesByCollection()).blockingGet();
        if (failures.isEmpty()) {
            log.info("Mongo index changeset {} of {} is in place", changeset.id(), declarations.source());
            return true;
        }

        failedChangeset.compareAndSet(null, changeset.id());
        return fail(changeset, failures.stream().map(MongoIndexReport.IndexOutcome::toString).collect(Collectors.joining(", ")));
    }

    private boolean fail(MongoIndexDeclarations.Changeset changeset, String reason) {
        if (upgradeOnly) {
            log.error("Mongo index changeset {} of {} could not be applied: {}", changeset.id(), declarations.source(), reason);
            return false;
        }
        throw new IllegalStateException("Mongo index changeset " + changeset.id() + " of " + declarations.source()
                + " could not be applied, it will be retried on next start: " + reason);
    }
}
