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
package io.gravitee.am.dataplane.mongodb.provider;

import io.gravitee.am.dataplane.api.upgrader.DataPlaneUpgrader;
import io.gravitee.am.dataplane.mongodb.upgrader.MongoDataPlaneIndexUpgrader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoDataPlaneProviderTest {

    @Mock
    private MongoDataPlaneIndexUpgrader.Changelog indexChangelog;

    @Mock
    private DataPlaneUpgrader indexUpgrader;

    @InjectMocks
    private MongoDataPlaneProvider provider;

    @Test
    void shouldProvideTheIndexUpgradersByDefault() {
        when(indexChangelog.isEnabled()).thenReturn(true);
        when(indexChangelog.upgraders()).thenReturn(List.of(indexUpgrader));

        assertThat(provider.getUpgraders()).containsExactly(indexUpgrader);
    }

    @Test
    void shouldProvideNoUpgraderWhenIndexManagementIsDisabled() {
        when(indexChangelog.isEnabled()).thenReturn(false);

        assertThat(provider.getUpgraders()).isEmpty();
    }
}
