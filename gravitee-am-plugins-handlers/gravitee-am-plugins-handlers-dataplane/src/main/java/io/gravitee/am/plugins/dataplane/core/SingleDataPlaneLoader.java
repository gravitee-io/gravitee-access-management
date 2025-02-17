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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class SingleDataPlaneLoader implements DataPlaneLoader {
    private static final String DATA_PLANE_KEY = "repositories.gateway";
    private static final String DATA_PLANE_ID_KEY = DATA_PLANE_KEY + ".dataPlane.id";
    private static final String DATA_PLANE_GW_URL_KEY = DATA_PLANE_KEY + ".dataPlane.url";
    private static final String DATA_PLANE_TYPE_KEY = DATA_PLANE_KEY + ".type";

    @Autowired
    protected Configuration configuration;

    @Override
    public void load(Consumer<DataPlaneDescription> storage) {
        var dataPlaneId = configuration.getProperty(DATA_PLANE_ID_KEY, String.class, "default");
        var dataPlaneUrl = configuration.getProperty(DATA_PLANE_GW_URL_KEY, String.class);
        var dataPlaneType = configuration.getProperty(DATA_PLANE_TYPE_KEY, String.class, "mongodb");
        storage.accept(new DataPlaneDescription(dataPlaneId, dataPlaneId, dataPlaneType, DATA_PLANE_KEY, dataPlaneUrl));
    }
}
