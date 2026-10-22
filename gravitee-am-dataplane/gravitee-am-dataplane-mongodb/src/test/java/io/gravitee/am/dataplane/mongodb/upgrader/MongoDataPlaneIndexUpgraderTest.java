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
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.repository.mongodb.common.MongoIndexDeclarations;
import io.gravitee.common.util.Version;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author GraviteeSource Team
 */
public class MongoDataPlaneIndexUpgraderTest {

    @Test
    public void shouldIdentifyTheUpgraderPerDataPlaneRuntimeVersionAndDeclarationsChecksum() {
        MockEnvironment environment = new MockEnvironment();
        MongoDataPlaneIndexUpgrader upgrader = new MongoDataPlaneIndexUpgrader(mock(MongoDatabase.class), new RepositoriesEnvironment(environment));
        DataPlaneDescription description = new DataPlaneDescription("default", "Default", "mongo", "repositories.gateway", "http://gateway");

        String version = Version.RUNTIME_VERSION.MAJOR_VERSION + "_" + MongoIndexDeclarations.load(getClass().getClassLoader(),
                MongoDataPlaneIndexUpgrader.RESOURCE, environment::resolveRequiredPlaceholders).checksum();

        assertThat(upgrader.version()).isEqualTo(version);
        assertThat(upgrader.identifier(description)).isEqualTo("default_" + MongoDataPlaneIndexUpgrader.class.getName() + "_" + version);
    }
}
