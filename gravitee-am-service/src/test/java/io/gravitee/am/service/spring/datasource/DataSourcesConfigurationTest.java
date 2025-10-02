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
import org.junit.Test;
import org.mockito.MockedStatic;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link DataSourcesConfiguration}
 *
 * @author GraviteeSource Team
 */
public class DataSourcesConfigurationTest {

    private DataSourcesConfiguration createConfigurationWithProperties(Map<String, Object> properties) {
        ConfigurableEnvironment environment = org.mockito.Mockito.mock(ConfigurableEnvironment.class);
        
        try (MockedStatic<EnvironmentUtils> mockedEnvironmentUtils = mockStatic(EnvironmentUtils.class)) {
            mockedEnvironmentUtils.when(() -> EnvironmentUtils.getPropertiesStartingWith(any(ConfigurableEnvironment.class), eq("datasources.")))
                    .thenReturn(properties);
            
            return new DataSourcesConfiguration(environment);
        }
    }

    @Test
    public void testConstructor_WithEmptyProperties() {
        ConfigurableEnvironment environment = org.mockito.Mockito.mock(ConfigurableEnvironment.class);
        
        try (MockedStatic<EnvironmentUtils> mockedEnvironmentUtils = mockStatic(EnvironmentUtils.class)) {
            mockedEnvironmentUtils.when(() -> EnvironmentUtils.getPropertiesStartingWith(any(ConfigurableEnvironment.class), eq("datasources.")))
                    .thenReturn(new HashMap<>());
            
            DataSourcesConfiguration config = new DataSourcesConfiguration(environment);
            
            assertThat(config.getDataSources()).isEmpty();
            assertThat(config.getDataSourcesAsSet()).isEmpty();
        }
    }

    @Test
    public void testConstructor_WithValidSingleDataSource() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "MongoDB Database");
        properties.put("datasources.mongo[0].description", "Primary MongoDB database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Map<String, DataSource> dataSourcesMap = config.getDataSources();
        Set<DataSource> dataSourcesSet = config.getDataSourcesAsSet();

        assertThat(dataSourcesMap).hasSize(1);
        assertThat(dataSourcesSet).hasSize(1);
        
        // Check the map contains the correct key
        assertThat(dataSourcesMap).containsKey("datasources.mongo[0]");
        
        DataSource dataSource = dataSourcesMap.get("datasources.mongo[0]");
        assertThat(dataSource.getId()).isEqualTo("mongo-db-1");
        assertThat(dataSource.name()).isEqualTo("MongoDB Database");
        assertThat(dataSource.description()).isEqualTo("Primary MongoDB database");
        
        // Check the set contains the same datasource
        DataSource dataSourceFromSet = dataSourcesSet.iterator().next();
        assertThat(dataSourceFromSet.getId()).isEqualTo("mongo-db-1");
        assertThat(dataSourceFromSet.name()).isEqualTo("MongoDB Database");
        assertThat(dataSourceFromSet.description()).isEqualTo("Primary MongoDB database");
    }

    @Test
    public void testConstructor_WithDataSourceMissingName() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].description", "Primary MongoDB database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Set<DataSource> dataSources = config.getDataSourcesAsSet();

        assertThat(dataSources).hasSize(1);
        
        DataSource dataSource = dataSources.iterator().next();
        assertThat(dataSource.getId()).isEqualTo("mongo-db-1");
        assertThat(dataSource.name()).isEqualTo("mongo-db-1"); // Should default to id
        assertThat(dataSource.description()).isEqualTo("Primary MongoDB database");
    }

    @Test
    public void testConstructor_WithDataSourceMissingDescription() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "MongoDB Database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Set<DataSource> dataSources = config.getDataSourcesAsSet();

        assertThat(dataSources).hasSize(1);
        
        DataSource dataSource = dataSources.iterator().next();
        assertThat(dataSource.getId()).isEqualTo("mongo-db-1");
        assertThat(dataSource.name()).isEqualTo("MongoDB Database");
        assertThat(dataSource.description()).isEqualTo("mongo datasource (index: 0)");
    }

    @Test
    public void testConstructor_WithDataSourceMissingId() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].name", "MongoDB Database");
        properties.put("datasources.mongo[0].description", "Primary MongoDB database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Set<DataSource> dataSources = config.getDataSourcesAsSet();

        assertThat(dataSources).isEmpty();
    }

    @Test
    public void testConstructor_WithEmptyId() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "");
        properties.put("datasources.mongo[0].name", "MongoDB Database");
        properties.put("datasources.mongo[0].description", "Primary MongoDB database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Set<DataSource> dataSources = config.getDataSourcesAsSet();

        assertThat(dataSources).isEmpty();
    }

    @Test
    public void testConstructor_WithWhitespaceId() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "   ");
        properties.put("datasources.mongo[0].name", "MongoDB Database");
        properties.put("datasources.mongo[0].description", "Primary MongoDB database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Set<DataSource> dataSources = config.getDataSourcesAsSet();

        assertThat(dataSources).isEmpty();
    }

    @Test
    public void testConstructor_WithMultipleDataSourcesOfSameType() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "Primary MongoDB");
        properties.put("datasources.mongo[0].description", "Primary database");
        
        properties.put("datasources.mongo[1].id", "mongo-db-2");
        properties.put("datasources.mongo[1].name", "Secondary MongoDB");
        properties.put("datasources.mongo[1].description", "Secondary database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Map<String, DataSource> dataSourcesMap = config.getDataSources();

        assertThat(dataSourcesMap).hasSize(2);
        
        // Check the map contains the correct keys
        assertThat(dataSourcesMap).containsKey("datasources.mongo[0]");
        assertThat(dataSourcesMap).containsKey("datasources.mongo[1]");
        
        DataSource primaryDb = dataSourcesMap.get("datasources.mongo[0]");
        DataSource secondaryDb = dataSourcesMap.get("datasources.mongo[1]");

        assertThat(primaryDb).isNotNull();
        assertThat(primaryDb.getId()).isEqualTo("mongo-db-1");
        assertThat(primaryDb.name()).isEqualTo("Primary MongoDB");
        assertThat(primaryDb.description()).isEqualTo("Primary database");

        assertThat(secondaryDb).isNotNull();
        assertThat(secondaryDb.getId()).isEqualTo("mongo-db-2");
        assertThat(secondaryDb.name()).isEqualTo("Secondary MongoDB");
        assertThat(secondaryDb.description()).isEqualTo("Secondary database");
    }

    @Test
    public void testConstructor_WithMultipleDataSourcesOfDifferentTypes() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "MongoDB Database");
        properties.put("datasources.mongo[0].description", "MongoDB database");
        
        properties.put("datasources.postgres[0].id", "postgres-db-1");
        properties.put("datasources.postgres[0].name", "PostgreSQL Database");
        properties.put("datasources.postgres[0].description", "PostgreSQL database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Map<String, DataSource> dataSourcesMap = config.getDataSources();

        assertThat(dataSourcesMap).hasSize(2);
        
        // Check the map contains the correct keys
        assertThat(dataSourcesMap).containsKey("datasources.mongo[0]");
        assertThat(dataSourcesMap).containsKey("datasources.postgres[0]");
        
        DataSource mongoDb = dataSourcesMap.get("datasources.mongo[0]");
        DataSource postgresDb = dataSourcesMap.get("datasources.postgres[0]");

        assertThat(mongoDb).isNotNull();
        assertThat(mongoDb.getId()).isEqualTo("mongo-db-1");
        assertThat(mongoDb.name()).isEqualTo("MongoDB Database");
        assertThat(mongoDb.description()).isEqualTo("MongoDB database");

        assertThat(postgresDb).isNotNull();
        assertThat(postgresDb.getId()).isEqualTo("postgres-db-1");
        assertThat(postgresDb.name()).isEqualTo("PostgreSQL Database");
        assertThat(postgresDb.description()).isEqualTo("PostgreSQL database");
    }

    @Test
    public void testConstructor_WithMalformedPropertyKeys() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo.id", "mongo-db-1"); // Missing index
        properties.put("datasources.mongo[].id", "mongo-db-2"); // Empty index
        properties.put("datasources.mongo[0].id", "mongo-db-3"); // Valid
        properties.put("datasources.mongo[0].name", "MongoDB Database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Set<DataSource> dataSources = config.getDataSourcesAsSet();

        assertThat(dataSources).hasSize(1);
        
        DataSource dataSource = dataSources.iterator().next();
        assertThat(dataSource.getId()).isEqualTo("mongo-db-3");
        assertThat(dataSource.name()).isEqualTo("MongoDB Database");
    }

    @Test
    public void testConstructor_WithNullValues() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", null);
        properties.put("datasources.mongo[0].description", null);

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Set<DataSource> dataSources = config.getDataSourcesAsSet();

        assertThat(dataSources).hasSize(1);
        
        DataSource dataSource = dataSources.iterator().next();
        assertThat(dataSource.getId()).isEqualTo("mongo-db-1");
        assertThat(dataSource.name()).isEqualTo("mongo-db-1"); // Should default to id
        assertThat(dataSource.description()).isEqualTo("mongo datasource (index: 0)");
    }

    @Test
    public void testGetDataSources_ReturnsMapWithPrefixKeys() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "MongoDB Database");
        properties.put("datasources.postgres[0].id", "postgres-db-1");
        properties.put("datasources.postgres[0].name", "PostgreSQL Database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Map<String, DataSource> dataSourcesMap = config.getDataSources();

        assertThat(dataSourcesMap).hasSize(2);
        assertThat(dataSourcesMap).containsKey("datasources.mongo[0]");
        assertThat(dataSourcesMap).containsKey("datasources.postgres[0]");
        
        // Verify the keys are exactly as expected
        assertThat(dataSourcesMap.keySet()).containsExactlyInAnyOrder(
                "datasources.mongo[0]", 
                "datasources.postgres[0]"
        );
    }

    @Test
    public void testGetDataSourcesAsSet_ReturnsSetOfDataSources() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "MongoDB Database");
        properties.put("datasources.postgres[0].id", "postgres-db-1");
        properties.put("datasources.postgres[0].name", "PostgreSQL Database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        Set<DataSource> dataSourcesSet = config.getDataSourcesAsSet();

        assertThat(dataSourcesSet).hasSize(2);
        
        // Verify we can find both datasources by their IDs
        DataSource mongoDb = dataSourcesSet.stream()
                .filter(ds -> "mongo-db-1".equals(ds.getId()))
                .findFirst()
                .orElse(null);
        
        DataSource postgresDb = dataSourcesSet.stream()
                .filter(ds -> "postgres-db-1".equals(ds.getId()))
                .findFirst()
                .orElse(null);

        assertThat(mongoDb).isNotNull();
        assertThat(postgresDb).isNotNull();
    }

    @Test
    public void testGetDataSourceKeyById_WithValidId() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "MongoDB Database");
        properties.put("datasources.mongo[0].description", "Primary MongoDB database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        
        String key = config.getDataSourceKeyById("mongo-db-1");
        
        assertThat(key).isEqualTo("datasources.mongo[0]");
    }

    @Test
    public void testGetDataSourceKeyById_WithMultipleDataSources() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "Primary MongoDB");
        properties.put("datasources.mongo[0].description", "Primary database");
        
        properties.put("datasources.mongo[1].id", "mongo-db-2");
        properties.put("datasources.mongo[1].name", "Secondary MongoDB");
        properties.put("datasources.mongo[1].description", "Secondary database");
        
        properties.put("datasources.postgres[0].id", "postgres-db-1");
        properties.put("datasources.postgres[0].name", "PostgreSQL Database");
        properties.put("datasources.postgres[0].description", "PostgreSQL database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        
        // Test finding first MongoDB datasource
        String mongoKey1 = config.getDataSourceKeyById("mongo-db-1");
        assertThat(mongoKey1).isEqualTo("datasources.mongo[0]");
        
        // Test finding second MongoDB datasource
        String mongoKey2 = config.getDataSourceKeyById("mongo-db-2");
        assertThat(mongoKey2).isEqualTo("datasources.mongo[1]");
        
        // Test finding PostgreSQL datasource
        String postgresKey = config.getDataSourceKeyById("postgres-db-1");
        assertThat(postgresKey).isEqualTo("datasources.postgres[0]");
    }

    @Test
    public void testGetDataSourceKeyById_WithNonExistentId() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "MongoDB Database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        
        String key = config.getDataSourceKeyById("non-existent-id");
        
        assertThat(key).isNull();
    }

    @Test
    public void testGetDataSourceKeyById_WithEmptyDataSources() {
        DataSourcesConfiguration config = createConfigurationWithProperties(new HashMap<>());
        
        String key = config.getDataSourceKeyById("any-id");
        
        assertThat(key).isNull();
    }

    @Test
    public void testGetDataSourceKeyById_WithNullId() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "MongoDB Database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        
        String key = config.getDataSourceKeyById(null);
        
        assertThat(key).isNull();
    }

    @Test
    public void testGetDataSourceKeyById_WithEmptyId() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "MongoDB Database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        
        String key = config.getDataSourceKeyById("");
        
        assertThat(key).isNull();
    }

    @Test
    public void testGetDataSourceKeyById_WithWhitespaceId() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "MongoDB Database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        
        String key = config.getDataSourceKeyById("   ");
        
        assertThat(key).isNull();
    }

    @Test
    public void testGetDataSourceKeyById_WithCaseSensitiveId() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("datasources.mongo[0].id", "mongo-db-1");
        properties.put("datasources.mongo[0].name", "MongoDB Database");

        DataSourcesConfiguration config = createConfigurationWithProperties(properties);
        
        // Test case sensitivity - should not find with different case
        String key = config.getDataSourceKeyById("MONGO-DB-1");
        
        assertThat(key).isNull();
    }
}
