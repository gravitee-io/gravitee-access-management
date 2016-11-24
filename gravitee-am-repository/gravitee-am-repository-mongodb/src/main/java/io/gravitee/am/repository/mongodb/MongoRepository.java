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
package io.gravitee.am.repository.mongodb;

import io.gravitee.am.repository.Repository;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.mongodb.oauth2.token.TokenRepositoryConfiguration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoRepository implements Repository {

    @Override
    public String type() {
        return "mongodb";
    }

    @Override
    public Scope[] scopes() {
        return new Scope [] { Scope.OAUTH2_TOKEN, Scope.OAUTH2_MANAGAMENT };
    }

    @Override
    public Class<?> configuration(Scope scope) {
        switch (scope) {
            case OAUTH2_TOKEN:
                return TokenRepositoryConfiguration.class;
        }

        return null;
    }
}
