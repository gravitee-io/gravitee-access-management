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
import io.gravitee.am.repository.mongodb.gateway.upgrader.MongoGatewayIndexUpgrader;
import io.gravitee.am.repository.mongodb.oauth2.upgrader.MongoOAuth2IndexUpgrader;
import io.gravitee.common.util.Version;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author GraviteeSource Team
 */
public class MongoIndexUpgradersTest {

    @Test
    public void shouldLoadEveryShippedDeclarationFile() {
        List.of(MongoOAuth2IndexUpgrader.RESOURCE, MongoGatewayIndexUpgrader.RESOURCE)
                .forEach(resource -> assertThat(load(resource, new MockEnvironment()).collections()).isNotEmpty());
    }

    @Test
    public void shouldIdentifyEachUpgraderByRuntimeVersionAndDeclarationsChecksum() {
        MockEnvironment environment = new MockEnvironment();
        MongoOAuth2IndexUpgrader upgrader = new MongoOAuth2IndexUpgrader(mock(MongoDatabase.class), new RepositoriesEnvironment(environment));

        assertThat(upgrader.version())
                .isEqualTo(Version.RUNTIME_VERSION.MAJOR_VERSION + "_" + load(MongoOAuth2IndexUpgrader.RESOURCE, environment).checksum());
        assertThat(upgrader.identifier()).isEqualTo(MongoOAuth2IndexUpgrader.class.getName() + "_" + upgrader.version());
    }

    @Test
    public void shouldRunBeforeEveryDataUpgrader() {
        assertThat(new MongoGatewayIndexUpgrader(mock(MongoDatabase.class), new RepositoriesEnvironment(new MockEnvironment())).getOrder())
                .isLessThan(-100);
    }

    private static MongoIndexDeclarations load(String resource, MockEnvironment environment) {
        return MongoIndexDeclarations.load(MongoIndexUpgradersTest.class.getClassLoader(), resource, environment::resolveRequiredPlaceholders);
    }
}
