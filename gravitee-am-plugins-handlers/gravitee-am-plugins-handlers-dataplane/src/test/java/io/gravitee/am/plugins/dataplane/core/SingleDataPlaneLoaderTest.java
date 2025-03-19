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

import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.node.api.configuration.Configuration;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;

public class SingleDataPlaneLoaderTest {

    @Test
    public void should_load_data_plane_description() {
        Configuration configuration = Mockito.mock(Configuration.class);
        Mockito.when(configuration.getProperty("repositories.gateway.type", String.class, "mongodb")).thenReturn("jdbc");
        Mockito.when(configuration.getProperty("repositories.gateway.dataPlane.id", String.class, "default")).thenReturn("id");
        Mockito.when(configuration.getProperty("repositories.gateway.dataPlane.url", String.class)).thenReturn("http://url.com");

        SingleDataPlaneLoader loader = new SingleDataPlaneLoader(configuration, "http://url2.com");

        Consumer<DataPlaneDescription> verifier = Mockito.mock(Consumer.class);
        loader.load(verifier);
        Mockito.verify(verifier, Mockito.times(1)).accept(
                argThat(
                        dp -> dp.type().equals("jdbc") &&
                        dp.id().equals("id") &&
                        dp.gatewayUrl().equals("http://url.com")));
    }

    @Test
    public void should_load_data_plane_gateway_url_fallback() {
        Configuration configuration = Mockito.mock(Configuration.class);
        Mockito.when(configuration.getProperty("repositories.gateway.type", String.class, "mongodb")).thenReturn("jdbc");
        Mockito.when(configuration.getProperty("repositories.gateway.dataPlane.id", String.class, "default")).thenReturn("id");

        SingleDataPlaneLoader loader = new SingleDataPlaneLoader(configuration, "http://url2.com");

        Consumer<DataPlaneDescription> verifier = Mockito.mock(Consumer.class);
        loader.load(verifier);
        Mockito.verify(verifier, Mockito.times(1)).accept(argThat(dp -> dp.gatewayUrl().equals("http://url2.com")));
    }

}