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
import io.gravitee.am.dataplane.api.DataPlaneProvider;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SingleDataPlaneProviderTest {

    @Test
    public void there_must_be_single_dataplane_registered(){
        // given
        DataPlaneProvider provider = Mockito.mock();
        DataPlaneRegistry registry = Mockito.mock();
        Mockito.when(registry.getDataPlanes()).thenReturn(List.of(new DataPlaneDescription("id", "name", "jdbc", "base", "gw")));
        Mockito.when(registry.getProviderById("id")).thenReturn(provider);

        // expect
        SingleDataPlaneProvider singleDataPlaneProvider = new SingleDataPlaneProvider(registry);
        Assertions.assertNotNull(singleDataPlaneProvider.get());
    }

    @Test
    public void throw_an_ex_when_there_are_more_than_one_data_planes(){
        // given
        DataPlaneProvider provider = Mockito.mock();
        DataPlaneRegistry registry = Mockito.mock();
        Mockito.when(registry.getDataPlanes()).thenReturn(List.of(new DataPlaneDescription("id", "name", "jdbc", "base", "gw"), new DataPlaneDescription("id2", "name", "jdbc", "base", "gw")));
        Mockito.when(registry.getProviderById("id")).thenReturn(provider);

        // expect
        SingleDataPlaneProvider singleDataPlaneProvider = new SingleDataPlaneProvider(registry);
        Assertions.assertThrowsExactly(IllegalStateException.class, () -> singleDataPlaneProvider.get());
    }

    @Test
    public void throw_an_ex_when_there_are_no_data_planes(){
        // given
        DataPlaneProvider provider = Mockito.mock();
        DataPlaneRegistry registry = Mockito.mock();
        Mockito.when(registry.getDataPlanes()).thenReturn(List.of());
        Mockito.when(registry.getProviderById("id")).thenReturn(provider);

        // expect
        SingleDataPlaneProvider singleDataPlaneProvider = new SingleDataPlaneProvider(registry);
        Assertions.assertThrowsExactly(IllegalStateException.class, () -> singleDataPlaneProvider.get());
    }

}