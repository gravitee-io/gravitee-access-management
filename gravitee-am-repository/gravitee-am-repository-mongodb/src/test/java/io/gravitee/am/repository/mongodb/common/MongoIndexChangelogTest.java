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
import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.node.api.upgrader.Upgrader;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

/**
 * @author GraviteeSource Team
 */
public class MongoIndexChangelogTest {

    @Test
    public void shouldCreateOneUpgraderPerChangesetInFileOrder() {
        assertThat(new TestChangelog(true).upgraders())
                .extracting(Upgrader::identifier, Upgrader::getOrder)
                .containsExactly(
                        tuple(TestIndexUpgrader.class.getName() + "_first", MongoIndexUpgrade.ORDER),
                        tuple(TestIndexUpgrader.class.getName() + "_second", MongoIndexUpgrade.ORDER + 1),
                        tuple(TestIndexUpgrader.class.getName() + "_third", MongoIndexUpgrade.ORDER + 2));
    }

    @Test
    public void shouldExposeEveryChangesetUpgraderToTheParentContext() {
        AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
        parent.refresh();

        startPluginContext(parent, new TestChangelog(true));

        assertThat(parent.getBeansWithAnnotation(ManagementRepositoryScope.class).keySet()).containsExactlyInAnyOrder(
                TestIndexUpgrader.class.getName() + "_first",
                TestIndexUpgrader.class.getName() + "_second",
                TestIndexUpgrader.class.getName() + "_third");
    }

    @Test
    public void shouldExposeNoChangesetUpgraderWhenIndexManagementIsDisabled() {
        AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
        parent.refresh();

        startPluginContext(parent, new TestChangelog(false));

        assertThat(parent.getBeansOfType(Upgrader.class)).isEmpty();
    }

    private static void startPluginContext(AnnotationConfigApplicationContext parent, MongoIndexChangelog changelog) {
        AnnotationConfigApplicationContext plugin = new AnnotationConfigApplicationContext();
        plugin.setParent(parent);
        plugin.registerBean(Configuration.class, () -> mock(Configuration.class));
        plugin.registerBean(MongoIndexChangelog.class, () -> changelog);
        plugin.register(MongoUpgraderConfiguration.class);
        plugin.refresh();
    }

    private static final class TestChangelog extends MongoIndexChangelog {

        private TestChangelog(boolean enabled) {
            super(mock(MongoDatabase.class), new RepositoriesEnvironment(new MockEnvironment()), "mongodb/indexes/test-changelog.json",
                    enabled, TestIndexUpgrader::new);
        }
    }

    @ManagementRepositoryScope
    private static final class TestIndexUpgrader extends AbstractMongoIndexUpgrader {

        private TestIndexUpgrader(MongoIndexUpgrade upgrade, MongoIndexDeclarations.Changeset changeset, int position) {
            super(upgrade, changeset, position);
        }
    }
}
