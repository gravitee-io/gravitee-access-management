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
import io.gravitee.am.common.env.RepositoriesEnvironment;

import java.util.List;
import java.util.stream.IntStream;

/**
 * @author GraviteeSource Team
 */
public abstract class MongoIndexChangelog {

    @FunctionalInterface
    protected interface UpgraderFactory {
        AbstractMongoIndexUpgrader create(MongoIndexUpgrade upgrade, MongoIndexDeclarations.Changeset changeset, int position);
    }

    private final String resource;
    private final boolean enabled;
    private final List<AbstractMongoIndexUpgrader> upgraders;

    protected MongoIndexChangelog(MongoDatabase database, RepositoriesEnvironment environment, String resource, boolean enabled, UpgraderFactory factory) {
        final MongoIndexDeclarations declarations = MongoIndexDeclarations.load(getClass().getClassLoader(), resource);
        final boolean upgradeOnly = environment.getDelegate().getProperty(MongoIndexUpgrade.UPGRADE_MODE_PROPERTY, Boolean.class, false);
        final MongoIndexUpgrade upgrade = new MongoIndexUpgrade(database, declarations, upgradeOnly);
        final List<MongoIndexDeclarations.Changeset> changesets = upgrade.changesets();
        this.resource = resource;
        this.enabled = enabled;
        this.upgraders = IntStream.range(0, changesets.size())
                .mapToObj(position -> factory.create(upgrade, changesets.get(position), position))
                .toList();
    }

    public String resource() {
        return resource;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<AbstractMongoIndexUpgrader> upgraders() {
        return upgraders;
    }
}
