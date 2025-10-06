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
package io.gravitee.am.service.spring.datasource;

import io.gravitee.am.model.DataSource;
import io.gravitee.common.util.EnvironmentUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Configuration
public class DataSourcesConfiguration {

    private static final String DATASOURCES_PREFIX = "datasources.";
    private final ConfigurableEnvironment environment;

    private Map<String, DataSource> dataSources;

    public DataSourcesConfiguration(ConfigurableEnvironment environment) {
        this.environment = environment;
        this.buildDataSources();
    }

    public String getDataSourceKeyById(String id){
        return dataSources.entrySet().stream()
                .filter(entry -> entry.getValue().getId().equals(id))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void buildDataSources() {
        dataSources = new HashMap<>();

        if(environment == null) {
            return;
        }

        var properties = EnvironmentUtils.getPropertiesStartingWith(environment, DATASOURCES_PREFIX);
        parseDatasource(properties);
    }

    private void parseDatasource(Map<String, Object> properties) {
        Map<String, Map<String, Map<String, String>>> groupedByTypeAndIndex = new HashMap<>();

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue().toString() : "";

            String relativeKey = key.substring(DATASOURCES_PREFIX.length());

            if (relativeKey.matches("^[a-zA-Z0-9_-]+\\[\\d+]\\..+$")) {
                String[] parts = relativeKey.split("\\[|]\\.", 3);
                if (parts.length >= 3) {
                    String type = parts[0];
                    String index = parts[1];
                    String property = parts[2];

                    groupedByTypeAndIndex
                            .computeIfAbsent(type, k -> new HashMap<>())
                            .computeIfAbsent(index, k -> new HashMap<>())
                            .put(property, value);
                }
            }
        }

        // Convert to DataSource objects and store with prefix keys
        for (Map.Entry<String, Map<String, Map<String, String>>> typeEntry : groupedByTypeAndIndex.entrySet()) {
            String type = typeEntry.getKey();
            Map<String, Map<String, String>> indices = typeEntry.getValue();

            for (Map.Entry<String, Map<String, String>> indexEntry : indices.entrySet()) {
                String index = indexEntry.getKey();
                Map<String, String> datasourceProperties = indexEntry.getValue();

                DataSource datasource = createDataSourceFromYamlProperties(type, index, datasourceProperties);
                if (datasource != null) {
                    String prefixKey = DATASOURCES_PREFIX + type + "[" + index + "]";
                    dataSources.put(prefixKey, datasource);
                }
            }
        }
    }

    private DataSource createDataSourceFromYamlProperties(String type, String index, Map<String, String> properties) {
        String id = properties.get("id");
        String name = properties.get("name");
        String description = properties.get("description");

        if (id == null || id.trim().isEmpty()) {
            return null;
        }

        if (name == null || name.trim().isEmpty()) {
            name = id;
        }

        if (description == null || description.trim().isEmpty()) {
            description = String.format("%s datasource (index: %s)", type, index);
        }

        return new DataSource(id, name, description);
    }

    public Map<String, DataSource> getDataSources() {
        return dataSources;
    }

    public Set<DataSource> getDataSourcesAsSet() {
        return new HashSet<>(dataSources.values());
    }
}
