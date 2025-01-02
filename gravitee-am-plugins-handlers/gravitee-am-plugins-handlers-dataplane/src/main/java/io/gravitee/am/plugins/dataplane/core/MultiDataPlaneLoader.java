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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.springframework.util.StringUtils.hasText;

@Component
public class MultiDataPlaneLoader extends DataPlaneLoader {
    private static final String DATA_PLANES_KEY = "dataPlanes";

    private Map<String, DataPlaneDescription> dataPlanDescriptions = new ConcurrentHashMap<>();

    public List<DataPlaneDescription> getDataPlanes() {
        return dataPlanDescriptions.values().stream().toList();
    }

    @Override
    protected void register() {
        for (var desc : readList()) {
            create(desc);
            dataPlanDescriptions.put(desc.id(), desc);
        }
    }

    private List<DataPlaneDescription> readList() {
        Function<Integer, String> propertyResolver = (idx) -> DATA_PLANES_KEY + "[" + idx + "]";
        List<DataPlaneDescription> result = new ArrayList<>();

        int i = 0;
        String base = propertyResolver.apply(i);
        while (configuration.containsProperty(base + ".id")) {
            result.add(readSingle(base));
            base = propertyResolver.apply(++i);
        }
        if(result.stream().noneMatch(DataPlaneDescription::isDefault)){
            throw new IllegalStateException("Default dataPlane is missing");
        }
        return result;
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
        return new DataPlaneDescription(dataPlanId, dataPlanName, dataPlanType, base);
    }
}
