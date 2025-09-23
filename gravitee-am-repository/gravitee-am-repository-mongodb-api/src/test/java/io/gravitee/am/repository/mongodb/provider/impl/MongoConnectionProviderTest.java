/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.repository.mongodb.provider.impl;

import com.mongodb.reactivestreams.client.MongoClient;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.provider.ClientWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongoConnectionProviderTest {

    @Mock
    private RepositoriesEnvironment environment;

    private MongoConnectionProvider provider;

    private static final String TEST_DATABASE_NAME = "test-db";
    private static final String TEST_MONGO_URI = "mongodb://localhost:27017/" + TEST_DATABASE_NAME;
    private static final String TEST_DATASOURCE_ID = "test-datasource";
    private static final String DATASOURCE_PREFIX = "datasources.mongodb[0].";

    @BeforeEach
    void setUp() {
        provider = new MongoConnectionProvider();
        provider.environment = environment;
    }

    @Test
    void shouldCreateClientBasedOnDatasourceId() {
        // Set up minimum environment properties to create a MongoClient
        when(environment.getProperty(DATASOURCE_PREFIX + "id", String.class)).thenReturn(TEST_DATASOURCE_ID);
        when(environment.getProperty(DATASOURCE_PREFIX + "settings.uri", String.class, null)).thenReturn(TEST_MONGO_URI);
        when(environment.getProperty(DATASOURCE_PREFIX + "settings.uri", "")).thenReturn(TEST_MONGO_URI);
        when(environment.getProperty(DATASOURCE_PREFIX + "settings.sslEnabled", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty(DATASOURCE_PREFIX + "settings.minHeartbeatFrequency", Integer.class, null)).thenReturn(5000);
        when(environment.getProperty(DATASOURCE_PREFIX + "settings.heartbeatFrequency", Integer.class, null)).thenReturn(5000);

        ClientWrapper<MongoClient> client = provider.getClientWrapperFromDatasource(TEST_DATASOURCE_ID);

        // Verify that the MongoClient was created with the correct database name
        assertThat(client).isNotNull();
        assertThat(client.getDatabaseName()).isEqualTo(TEST_DATABASE_NAME);
    }

    @Test
    void shouldNotCreateClientBasedOnDatasourceIdWhenIDNotFound() {
        // Handle the scenario where the datasource ID is not found in the environment
        assertThatThrownBy(() -> provider.getClientWrapperFromDatasource(TEST_DATASOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No datasource found with id: test-datasource");
    }
}