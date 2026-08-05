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
package io.gravitee.am.plugins.dataplane.core;

import io.gravitee.node.api.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class MultiDataPlaneLoaderTest {

    private final Configuration configuration = Mockito.mock(Configuration.class);

    private MultiDataPlaneLoader loader(String... ids) {
        for (int i = 0; i < ids.length; i++) {
            Mockito.when(configuration.containsProperty("dataPlanes[" + i + "].id")).thenReturn(true);
            Mockito.when(configuration.getProperty("dataPlanes[" + i + "].id", String.class)).thenReturn(ids[i]);
        }
        MultiDataPlaneLoader loader = new MultiDataPlaneLoader();
        loader.configuration = configuration;
        return loader;
    }

    @Test
    void should_recognise_a_declared_id() {
        assertThat(loader("default", "dp-yml").isDeclared("dp-yml")).isTrue();
    }

    @Test
    void should_not_recognise_an_id_beyond_the_declared_ones() {
        assertThat(loader("default", "dp-yml").isDeclared("dp-provisioned")).isFalse();
    }

    @Test
    void should_not_recognise_anything_when_nothing_is_declared() {
        assertThat(loader().isDeclared("default")).isFalse();
    }
}
