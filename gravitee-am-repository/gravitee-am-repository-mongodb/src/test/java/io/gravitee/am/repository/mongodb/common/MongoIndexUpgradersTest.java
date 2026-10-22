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
import io.gravitee.am.repository.mongodb.common.MongoIndexDeclarations.Changeset;
import io.gravitee.am.repository.mongodb.gateway.upgrader.MongoGatewayIndexUpgrader;
import io.gravitee.am.repository.mongodb.management.upgrader.MongoManagementIndexUpgrader;
import io.gravitee.am.repository.mongodb.oauth2.upgrader.MongoOAuth2IndexUpgrader;
import io.gravitee.node.api.upgrader.Upgrader;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

/**
 * @author GraviteeSource Team
 */
public class MongoIndexUpgradersTest {

    private static final RepositoriesEnvironment ENVIRONMENT = new RepositoriesEnvironment(new MockEnvironment());

    @Test
    public void shouldLoadEveryShippedDeclarationFile() {
        List.of(MongoManagementIndexUpgrader.RESOURCE, MongoOAuth2IndexUpgrader.RESOURCE, MongoGatewayIndexUpgrader.RESOURCE)
                .forEach(resource -> assertThat(load(resource).indexesByCollection()).isNotEmpty());
    }

    @Test
    public void shouldIdentifyEachUpgraderByItsChangeset() {
        assertThat(new MongoManagementIndexUpgrader.Changelog(mock(MongoDatabase.class), ENVIRONMENT).upgraders())
                .extracting(Upgrader::identifier)
                .containsExactly(MongoManagementIndexUpgrader.class.getName() + "_initial-indexes");
        assertThat(new MongoOAuth2IndexUpgrader.Changelog(mock(MongoDatabase.class), ENVIRONMENT).upgraders())
                .extracting(Upgrader::identifier)
                .containsExactly(MongoOAuth2IndexUpgrader.class.getName() + "_initial-indexes");
        assertThat(new MongoGatewayIndexUpgrader.Changelog(mock(MongoDatabase.class), ENVIRONMENT).upgraders())
                .extracting(Upgrader::identifier)
                .containsExactly(MongoGatewayIndexUpgrader.class.getName() + "_initial-indexes");
    }

    @Test
    public void shouldNeverChangeAChangeset() {
        assertThat(changesets(MongoManagementIndexUpgrader.RESOURCE)).extracting(Changeset::id, Changeset::checksum)
                .containsExactly(tuple("initial-indexes", "52a83bdac197"));
        assertThat(changesets(MongoOAuth2IndexUpgrader.RESOURCE)).extracting(Changeset::id, Changeset::checksum)
                .containsExactly(tuple("initial-indexes", "008287bfa02e"));
        assertThat(changesets(MongoGatewayIndexUpgrader.RESOURCE)).extracting(Changeset::id, Changeset::checksum)
                .containsExactly(tuple("initial-indexes", "973ec105e893"));
    }

    @Test
    public void shouldRunBeforeEveryDataUpgrader() {
        assertThat(new MongoGatewayIndexUpgrader.Changelog(mock(MongoDatabase.class), ENVIRONMENT).upgraders())
                .allMatch(upgrader -> upgrader.getOrder() < -100);
    }

    private static List<Changeset> changesets(String resource) {
        return load(resource).changesets();
    }

    private static MongoIndexDeclarations load(String resource) {
        return MongoIndexDeclarations.load(MongoIndexUpgradersTest.class.getClassLoader(), resource);
    }
}
