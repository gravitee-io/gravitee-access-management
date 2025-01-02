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
import org.springframework.stereotype.Component;

@Component
public class SingleDataPlaneLoader extends DataPlaneLoader {
    private static final String DATA_PLANE_KEY = "dataPlane";
    private static final String DATA_PLANE_ID_KEY = DATA_PLANE_KEY + ".id";
    private static final String DATA_PLANE_TYPE_KEY = DATA_PLANE_KEY + ".type";

    @Override
    protected void register() {
        var dataPlaneId = configuration.getProperty(DATA_PLANE_ID_KEY, String.class);
        var dataPlaneType = configuration.getProperty(DATA_PLANE_TYPE_KEY, String.class, "mongodb");
        var description = new DataPlaneDescription(dataPlaneId, dataPlaneId, dataPlaneType, DATA_PLANE_KEY);
        create(description);
    }
}
