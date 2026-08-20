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
package io.gravitee.am.identityprovider.mongo;

import com.mongodb.reactivestreams.client.MongoClient;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.gravitee.am.service.spring.datasource.DataSourcesConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class MongoAbstractProviderTest {

    private static final String SYSTEM_DATABASE = "gravitee-am";

    private final ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
    private final DataSourcesConfiguration dataSourcesConfiguration = mock(DataSourcesConfiguration.class);
    private final ClientWrapper<MongoClient> systemClientWrapper = mock(ClientWrapper.class);
    private final ClientWrapper<MongoClient> datasourceClientWrapper = mock(ClientWrapper.class);

    private MongoIdentityProviderConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new MongoIdentityProviderConfiguration();
        configuration.setUseSystemCluster(true);
        configuration.setDatabase("configured-db");

        when(connectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(connectionProvider.getClientWrapper(anyString())).thenReturn(systemClientWrapper);
        when(systemClientWrapper.getDatabaseName()).thenReturn(SYSTEM_DATABASE);
    }

    @Test
    void should_take_the_database_from_the_client_wrapper_for_a_restricted_provider() {
        var provider = provider(identityProvider(true));

        provider.afterPropertiesSet();

        assertEquals(SYSTEM_DATABASE, configuration.getDatabase());
    }

    @Test
    void should_keep_the_configured_database_for_a_provider_created_before_the_restriction() {
        var provider = provider(identityProvider(false));

        provider.afterPropertiesSet();

        assertEquals("configured-db", configuration.getDatabase());
    }

    @Test
    void should_keep_the_datasource_database_for_a_restricted_provider_naming_a_datasource() {
        configuration.setDatasourceId("ds-1");
        when(dataSourcesConfiguration.getDataSourceKeyById("ds-1")).thenReturn("datasources.ds1");
        when(connectionProvider.getClientWrapperFromDatasource("ds-1", "datasources.ds1.settings."))
                .thenReturn(datasourceClientWrapper);
        var provider = provider(identityProvider(true));
        ((MockEnvironment) ReflectionTestUtils.getField(provider, "environment"))
                .setProperty("datasources.ds1.settings.dbname", "datasource-db");

        provider.afterPropertiesSet();

        assertEquals("datasource-db", configuration.getDatabase());
    }

    private IdentityProvider identityProvider(boolean systemClusterRestricted) {
        var idp = new IdentityProvider();
        idp.setId("idp-1");
        idp.setType("mongo-am-idp");
        idp.setSystemClusterRestricted(systemClusterRestricted);
        return idp;
    }

    private MongoAbstractProvider provider(IdentityProvider identityProviderEntity) {
        var provider = new MongoAbstractProvider() {
        };
        ReflectionTestUtils.setField(provider, "commonConnectionProvider", connectionProvider);
        ReflectionTestUtils.setField(provider, "identityProviderEntity", identityProviderEntity);
        ReflectionTestUtils.setField(provider, "configuration", configuration);
        ReflectionTestUtils.setField(provider, "environment", new MockEnvironment());
        ReflectionTestUtils.setField(provider, "dataSourcesConfiguration", dataSourcesConfiguration);
        return provider;
    }
}
