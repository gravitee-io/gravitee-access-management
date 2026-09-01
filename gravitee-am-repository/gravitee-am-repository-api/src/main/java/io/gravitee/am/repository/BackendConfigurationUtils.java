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
package io.gravitee.am.repository;

import io.gravitee.am.common.env.RepositoriesEnvironment;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.URI;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BackendConfigurationUtils {
    public static final String SYSTEM_CLUSTER = "repositories.system-cluster";
    public static final String DEFAULT_SYSTEM_CLUSTER = Scope.MANAGEMENT.getName();

    static final String DEFAULT_MONGO_DATABASE = "gravitee-am";

    public static String getMongoDatabaseName(String propertiesBase, RepositoriesEnvironment environment) {
        final String prefix = propertiesBase + ".mongodb.";
        final String uri = environment.getProperty(prefix + "uri", "");
        if (!uri.isEmpty()) {
            final String path = URI.create(uri).getPath();
            if (path != null && path.length() > 1) {
                return path.substring(1);
            }
        }
        return environment.getProperty(prefix + "dbname", DEFAULT_MONGO_DATABASE);
    }

    public static String getMongoDatabaseName(RepositoriesEnvironment environment) {
        return getMongoDatabaseName(Scope.MANAGEMENT.getRepositoryPropertyKey(), environment);
    }
}
