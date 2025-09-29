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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.DataSource;
import io.gravitee.am.service.DataSourceService;
import io.gravitee.common.util.EnvironmentUtils;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class DataSourceServiceImpl implements DataSourceService {

    private static final String DATASOURCES_PREFIX = "datasources.";

    @Autowired
    private ConfigurableEnvironment environment;

    @Override
    public Flowable<DataSource> findAll() {

        try {
            Map<String, Object> datasourceProperties = EnvironmentUtils.getPropertiesStartingWith(environment, DATASOURCES_PREFIX);
            
            if (datasourceProperties.isEmpty()) {
                return Flowable.empty();
            }

            List<DataSource> datasources = parseYamlDatasourceStructure(datasourceProperties);

            log.debug("Found {} datasources in environment configuration", datasources.size());
            return Flowable.fromIterable(datasources);
            
        } catch (Exception e) {
            log.error("Error reading datasources from environment configuration", e);
            return Flowable.empty();
        }
    }

    private List<DataSource> parseYamlDatasourceStructure(Map<String, Object> properties) {
        Map<String, Map<String, Map<String, String>>> groupedByTypeAndIndex = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            
            // Parse YAML structure: datasources.{type}[{index}].{property}
            // e.g., "datasources.mongodb[0].id" -> type="mongodb", index="0", property="id"
            String relativeKey = key.substring(DATASOURCES_PREFIX.length());
            
            // Match pattern: {type}[{index}].{property}
            if (relativeKey.matches("^[a-zA-Z0-9_-]+\\[\\d+\\]\\..+$")) {
                String[] parts = relativeKey.split("\\[|\\]\\.", 3);
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
        
        // Convert to DataSource objects
        List<DataSource> datasources = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, String>>> typeEntry : groupedByTypeAndIndex.entrySet()) {
            String type = typeEntry.getKey();
            Map<String, Map<String, String>> indices = typeEntry.getValue();
            
            for (Map.Entry<String, Map<String, String>> indexEntry : indices.entrySet()) {
                String index = indexEntry.getKey();
                Map<String, String> datasourceProperties = indexEntry.getValue();
                
                DataSource datasource = createDataSourceFromYamlProperties(type, index, datasourceProperties);
                if (datasource != null) {
                    datasources.add(datasource);
                }
            }
        }
        
        return datasources;
    }

    private DataSource createDataSourceFromYamlProperties(String type, String index, Map<String, String> properties) {
        // Extract required properties
        String id = properties.get("id");
        String name = properties.get("name");
        String description = properties.get("description");
        
        // Validate required fields
        if (id == null || id.trim().isEmpty()) {
            log.warn("Datasource {}.{} is missing required 'id' property, skipping", type, index);
            return null;
        }
        
        // Make name optional - use id as fallback
        if (name == null || name.trim().isEmpty()) {
            name = id;
        }
        
        // Make description optional - include type info if not provided
        if (description == null || description.trim().isEmpty()) {
            description = String.format("%s datasource (index: %s)", type, index);
        }
        
        log.debug("Created datasource: id={}, name={}, description={}, type={}, index={}", 
                 id, name, description, type, index);
        return new DataSource(id, name, description);
    }
}