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
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class SingleDataPlaneProvider {
    private final DataPlaneRegistry registry;

    public DataPlaneProvider get() {
        List<DataPlaneDescription> descriptions = registry.getDataPlanes();
        if (descriptions.size() != 1) {
            // use case for gateway only
            throw new IllegalStateException("To use this method, there must be only one dataPlane registered");
        }
        DataPlaneDescription description = descriptions.get(0);
        return registry.getProviderById(description.id());
    }
}
