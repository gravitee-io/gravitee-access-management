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
package io.gravitee.am.dataplane.mongodb.upgrader;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.am.dataplane.api.upgrader.DataPlaneUpgrader;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.mongodb.common.MongoIndexDeclarations;
import io.gravitee.am.repository.mongodb.common.MongoIndexUpgrade;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoDataPlaneIndexUpgrader implements DataPlaneUpgrader {

    public static final String RESOURCE = "mongodb/indexes/dataplane.json";

    private final MongoIndexUpgrade upgrade;
    private final boolean enabled;

    public MongoDataPlaneIndexUpgrader(@Qualifier("dataPlaneMongoDatabase") MongoDatabase database, RepositoriesEnvironment environment) {
        final MongoIndexDeclarations declarations = MongoIndexDeclarations.load(getClass().getClassLoader(), RESOURCE,
                environment.getDelegate()::resolveRequiredPlaceholders);
        final boolean upgradeOnly = environment.getDelegate().getProperty(MongoIndexUpgrade.UPGRADE_MODE_PROPERTY, Boolean.class, false);
        this.upgrade = new MongoIndexUpgrade(database, declarations, upgradeOnly);
        this.enabled = environment.getProperty(Scope.GATEWAY.getRepositoryPropertyKey() + ".mongodb.ensureIndexOnStart", Boolean.class,
                environment.getProperty(Scope.OAUTH2.getRepositoryPropertyKey() + ".mongodb.ensureIndexOnStart", Boolean.class, true));
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean upgrade(DataPlaneProvider dataPlaneProvider) {
        return upgrade.apply();
    }

    @Override
    public String version() {
        return upgrade.version();
    }
}
