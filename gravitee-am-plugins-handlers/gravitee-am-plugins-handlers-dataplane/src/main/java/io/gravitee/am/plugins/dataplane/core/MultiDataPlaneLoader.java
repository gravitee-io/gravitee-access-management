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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.springframework.util.StringUtils.hasText;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MultiDataPlaneLoader implements DataPlaneLoader {
    private static final String DATA_PLANES_KEY = "dataPlanes";

    @Autowired
    protected Configuration configuration;

    @Override
    public void load(Consumer<DataPlaneDescription> storage) {
        for (var desc : readList()) {
            storage.accept(desc);
        }
    }

    /**
     * Whether the configuration declares a data plane under this id. Only the ids are read: a
     * declaration too broken to describe is the node's problem at startup, not the caller's.
     */
    public boolean isDeclared(String id) {
        for (int i = 0; configuration.containsProperty(propertyBase(i) + ".id"); i++) {
            if (id.equals(configuration.getProperty(propertyBase(i) + ".id", String.class))) {
                return true;
            }
        }
        return false;
    }

    private List<DataPlaneDescription> readList() {
        List<DataPlaneDescription> result = new ArrayList<>();

        int i = 0;
        String base = propertyBase(i);
        while (configuration.containsProperty(base + ".id")) {
            result.add(readSingle(base));
            base = propertyBase(++i);
        }
        if (result.stream().noneMatch(DataPlaneDescription::isDefault)) {
            throw new IllegalStateException("Default dataPlane is missing");
        }
        return result;
    }

    private static String propertyBase(int index) {
        return DATA_PLANES_KEY + "[" + index + "]";
    }

    private DataPlaneDescription readSingle(String base) {
        var dataPlanId = configuration.getProperty(base + ".id", String.class);
        if (!hasText(dataPlanId)) {
            throw new IllegalStateException("Invalid data plan definition, id is required");
        }
        var dataPlanType = configuration.getProperty(base + ".type", String.class);
        if (!hasText(dataPlanType)) {
            throw new IllegalStateException("Invalid data plan definition, type is required");
        }
        final var dataPlanName = configuration.getProperty(base + ".name", String.class, dataPlanId);

        final var dataPlaneUrl = configuration.getProperty(base + ".gateway.url", String.class);
        return new DataPlaneDescription(dataPlanId, dataPlanName, dataPlanType, base, dataPlaneUrl);
    }
}
