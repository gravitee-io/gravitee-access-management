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

import io.gravitee.node.api.upgrader.Upgrader;

/**
 * @author GraviteeSource Team
 */
public abstract class AbstractMongoIndexUpgrader implements Upgrader {

    private final MongoIndexUpgrade upgrade;
    private final MongoIndexDeclarations.Changeset changeset;
    private final int position;

    protected AbstractMongoIndexUpgrader(MongoIndexUpgrade upgrade, MongoIndexDeclarations.Changeset changeset, int position) {
        this.upgrade = upgrade;
        this.changeset = changeset;
        this.position = position;
    }

    @Override
    public boolean upgrade() {
        return upgrade.apply(changeset);
    }

    @Override
    public int getOrder() {
        return MongoIndexUpgrade.ORDER + position;
    }

    @Override
    public String version() {
        return changeset.id();
    }
}
