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
import io.gravitee.common.util.Version;
import lombok.CustomLog;

import java.util.List;
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
    private final String version;

    public MongoIndexUpgrade(MongoDatabase database, MongoIndexDeclarations declarations, boolean upgradeOnly) {
        this.database = database;
        this.declarations = declarations;
        this.upgradeOnly = upgradeOnly;
        this.version = Version.RUNTIME_VERSION.MAJOR_VERSION + "_" + declarations.checksum();
    }

    public String version() {
        return version;
    }

    public boolean apply() {
        log.info("Applying Mongo indexes declared in {} (version {}) to database {}",
                declarations.source(), version, database.getName());
        final List<MongoIndexReport.IndexOutcome> failures = MongoIndexManager.ensureDeclarations(database, declarations).blockingGet();
        if (failures.isEmpty()) {
            log.info("Mongo indexes declared in {} (version {}) are in place", declarations.source(), version);
            return true;
        }

        final String failed = failures.stream().map(MongoIndexReport.IndexOutcome::toString).collect(Collectors.joining(", "));
        if (upgradeOnly) {
            log.error("Mongo indexes declared in {} could not all be applied: {}", declarations.source(), failed);
            return false;
        }
        throw new IllegalStateException("Mongo indexes declared in " + declarations.source()
                + " could not all be applied, they will be retried on next start: " + failed);
    }
}
