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
package io.gravitee.am.repository.mongodb.oauth2.upgrader;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.common.scope.GatewayRepositoryScope;
import io.gravitee.am.repository.mongodb.common.AbstractMongoIndexUpgrader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * @author GraviteeSource Team
 */
@Component
@GatewayRepositoryScope
public class MongoOAuth2IndexUpgrader extends AbstractMongoIndexUpgrader {

    public static final String RESOURCE = "mongodb/indexes/oauth2.json";

    public MongoOAuth2IndexUpgrader(@Qualifier("oauth2MongoTemplate") MongoDatabase database, RepositoriesEnvironment environment) {
        super(database, environment, RESOURCE,
                environment.getProperty("repositories.oauth2.mongodb.ensureIndexOnStart", Boolean.class, true));
    }
}
