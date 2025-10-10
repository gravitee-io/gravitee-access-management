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
package io.gravitee.am.repository.mongodb.provider.impl;

import com.mongodb.reactivestreams.client.MongoClient;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.mongodb.provider.MongoConnectionConfiguration;
import io.gravitee.am.repository.mongodb.provider.MongoFactory;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static io.gravitee.am.repository.Scope.GATEWAY;
import static io.gravitee.am.repository.Scope.MANAGEMENT;
import static io.gravitee.am.repository.Scope.OAUTH2;
import static io.gravitee.am.repository.Scope.RATE_LIMIT;
import static io.gravitee.am.repository.provider.ConnectionProvider.BACKEND_TYPE_MONGO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MongoConnectionProvider}
 *
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MongoConnectionProviderTest {

    @Mock
    private RepositoriesEnvironment environment;

    @Mock
    private MongoFactory mongoFactory;

    @Mock
    private MongoClient mongoClient;

    @Mock
    private MongoConnectionConfiguration mongoConnectionConfiguration;

    private MongoConnectionProvider mongoConnectionProvider;

    @Before
    public void setUp() {
        mongoConnectionProvider = new MongoConnectionProvider();
        ReflectionTestUtils.setField(mongoConnectionProvider, "environment", environment);
        ReflectionTestUtils.setField(mongoConnectionProvider, "mongoFactory", mongoFactory);
    }

    @Test
    public void testAfterPropertiesSet_DefaultConfiguration() throws Exception {
        setupDefaultConfiguration();

        mongoConnectionProvider.afterPropertiesSet();

        verify(environment).getProperty(eq(OAUTH2.getRepositoryPropertyKey() + ".use-management-settings"), eq(Boolean.class), eq(true));
        verify(environment).getProperty(eq(OAUTH2.getRepositoryPropertyKey() + ".use-gateway-settings"), eq(Boolean.class), eq(false));
        verify(environment).getProperty(eq(GATEWAY.getRepositoryPropertyKey() + ".use-management-settings"), eq(Boolean.class), eq(true));
        verify(environment).getProperty(eq(MANAGEMENT.getRepositoryPropertyKey() + ".use-management-settings"), eq(Boolean.class), eq(true));
        verify(environment, atLeastOnce()).getProperty(eq(RATE_LIMIT.getRepositoryPropertyKey() + ".use-management-settings"), eq(Boolean.class), eq(true));
        verify(environment).getProperty(eq(RATE_LIMIT.getRepositoryPropertyKey() + ".use-gateway-settings"), eq(Boolean.class), eq(false));
        verify(mongoFactory, atLeastOnce()).getObject(any());
    }

    @Test
    public void testAfterPropertiesSet_OAuth2UsesOwnSettings() throws Exception {
        setBooleanProperty(OAUTH2.getRepositoryPropertyKey() + ".use-management-settings", true, false);
        setBooleanProperty(OAUTH2.getRepositoryPropertyKey() + ".use-gateway-settings", false, false);
        setBooleanProperty(GATEWAY.getRepositoryPropertyKey() + ".use-management-settings", true, true);
        setBooleanProperty(MANAGEMENT.getRepositoryPropertyKey() + ".use-management-settings", true, true);
        setBooleanProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".use-management-settings", true, false);
        setBooleanProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".use-gateway-settings", false, false);

        setDefaultDatabaseName();

        when(mongoFactory.getObject(any())).thenReturn(mongoClient);

        mongoConnectionProvider.afterPropertiesSet();

        verify(mongoFactory).getObject(MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.");
        verify(mongoFactory).getObject(OAUTH2.getRepositoryPropertyKey() + ".mongodb.");
    }

    @Test
    public void testAfterPropertiesSet_GatewayUsesOwnSettings() throws Exception {
        setBooleanProperty(OAUTH2.getRepositoryPropertyKey() + ".use-management-settings", true, true);
        setBooleanProperty(OAUTH2.getRepositoryPropertyKey() + ".use-gateway-settings", false, false);
        setBooleanProperty(GATEWAY.getRepositoryPropertyKey() + ".use-management-settings", true, false);
        setBooleanProperty(MANAGEMENT.getRepositoryPropertyKey() + ".use-management-settings", true, true);
        setBooleanProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".use-management-settings", true, false);
        setBooleanProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".use-gateway-settings", false, false);

        setDefaultDatabaseName();

        when(mongoFactory.getObject(any())).thenReturn(mongoClient);

        mongoConnectionProvider.afterPropertiesSet();

        verify(mongoFactory).getObject(MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.");
        verify(mongoFactory).getObject(GATEWAY.getRepositoryPropertyKey() + ".mongodb.");
    }

    @Test
    public void testGetClientWrapper_Default() throws Exception {
        setupDefaultConfiguration();
        mongoConnectionProvider.afterPropertiesSet();

        ClientWrapper<MongoClient> result = mongoConnectionProvider.getClientWrapper();

        assertThat(result).isNotNull();
    }

    @Test
    public void testGetClientWrapper_Management() throws Exception {
        setupDefaultConfiguration();
        mongoConnectionProvider.afterPropertiesSet();

        ClientWrapper<MongoClient> result = mongoConnectionProvider.getClientWrapper(MANAGEMENT.getName());

        assertThat(result).isNotNull();
    }

    @Test
    public void testGetClientWrapper_OAuth2UsesManagementSettings() throws Exception {
        setupDefaultConfiguration();
        mongoConnectionProvider.afterPropertiesSet();

        ClientWrapper<MongoClient> oauthWrapper = mongoConnectionProvider.getClientWrapper(OAUTH2.getName());
        ClientWrapper<MongoClient> mgmtWrapper = mongoConnectionProvider.getClientWrapper(MANAGEMENT.getName());

        assertThat(oauthWrapper).isNotNull();
        assertThat(mgmtWrapper).isNotNull();
        assertThat(oauthWrapper).isSameAs(mgmtWrapper);
    }

    @Test
    public void testGetClientWrapper_OAuth2UsesOwnSettings() throws Exception {
        setBooleanProperty(OAUTH2.getRepositoryPropertyKey() + ".use-management-settings", true, false);
        setBooleanProperty(OAUTH2.getRepositoryPropertyKey() + ".use-gateway-settings", false, false);
        setBooleanProperty(GATEWAY.getRepositoryPropertyKey() + ".use-management-settings", true, true);
        setBooleanProperty(MANAGEMENT.getRepositoryPropertyKey() + ".use-management-settings", true, true);
        setBooleanProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".use-management-settings", true, false);
        setBooleanProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".use-gateway-settings", false, false);

        setDefaultDatabaseName();

        when(mongoFactory.getObject(any())).thenReturn(mongoClient);
        mongoConnectionProvider.afterPropertiesSet();

        ClientWrapper<MongoClient> oauthWrapper = mongoConnectionProvider.getClientWrapper(OAUTH2.getName());
        ClientWrapper<MongoClient> mgmtWrapper = mongoConnectionProvider.getClientWrapper(MANAGEMENT.getName());

        assertThat(oauthWrapper).isNotNull();
        assertThat(mgmtWrapper).isNotNull();
        assertThat(oauthWrapper).isNotSameAs(mgmtWrapper);
    }

    @Test
    public void testGetClientWrapper_OAuth2UsesGatewaySettings() throws Exception {
        setupDefaultConfiguration();
        mongoConnectionProvider.afterPropertiesSet();

        ClientWrapper<MongoClient> oauthWrapper = mongoConnectionProvider.getClientWrapper(OAUTH2.getName());
        ClientWrapper<MongoClient> gatewayWrapper = mongoConnectionProvider.getClientWrapper(GATEWAY.getName());

        assertThat(oauthWrapper).isNotNull();
        assertThat(gatewayWrapper).isNotNull();
        assertThat(oauthWrapper).isSameAs(gatewayWrapper);
    }

    @Test
    public void testGetClientWrapper_GatewayUsesOwnSettings() throws Exception {
        setBooleanProperty(OAUTH2.getRepositoryPropertyKey() + ".use-management-settings", true, true);
        setBooleanProperty(OAUTH2.getRepositoryPropertyKey() + ".use-gateway-settings", false, false);
        setBooleanProperty(GATEWAY.getRepositoryPropertyKey() + ".use-management-settings", true, false);
        setBooleanProperty(MANAGEMENT.getRepositoryPropertyKey() + ".use-management-settings", true, true);
        setBooleanProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".use-management-settings", true, false);
        setBooleanProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".use-gateway-settings", false, false);

        setDefaultDatabaseName();

        when(mongoFactory.getObject(any())).thenReturn(mongoClient);
        mongoConnectionProvider.afterPropertiesSet();

        ClientWrapper<MongoClient> gatewayWrapper = mongoConnectionProvider.getClientWrapper(GATEWAY.getName());
        ClientWrapper<MongoClient> mgmtWrapper = mongoConnectionProvider.getClientWrapper(MANAGEMENT.getName());

        assertThat(gatewayWrapper).isNotNull();
        assertThat(mgmtWrapper).isNotNull();
        assertThat(gatewayWrapper).isNotSameAs(mgmtWrapper);
    }

    @Test
    public void testGetClientFromConfiguration() {
        when(mongoConnectionConfiguration.getUri()).thenReturn("mongodb://localhost:27017/test");

        ClientWrapper<MongoClient> result = mongoConnectionProvider.getClientFromConfiguration(mongoConnectionConfiguration);

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(MongoClientWrapper.class);
        verify(mongoFactory, never()).getObject(any());
    }

    @Test
    public void testGetClientWrapperFromDataplane() {
        String propertyPrefix = "dataPlanes[0]";

        setupDefaultConfiguration();

        setStringProperty(propertyPrefix + ".mongodb.uri", "");
        setStringProperty(propertyPrefix + ".mongodb.dbname", "gravitee-am");

        ClientWrapper<MongoClient> result = mongoConnectionProvider.getClientWrapperFromPrefix(propertyPrefix);

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(MongoClientWrapper.class);
        verify(environment).getProperty(propertyPrefix + ".mongodb.uri", "");
        verify(environment).getProperty(propertyPrefix + ".mongodb.dbname", "gravitee-am");
        verify(mongoFactory).getObject(propertyPrefix + ".mongodb.");
    }

    @Test
    public void testGetClientWrapperFromDatasource_NewDatasource() {
        String datasourceId = "test-datasource";
        String propertyPrefix = MANAGEMENT.getRepositoryPropertyKey();

        setupDefaultConfiguration();

        ClientWrapper<MongoClient> result = mongoConnectionProvider.getClientWrapperFromDatasource(datasourceId, propertyPrefix);

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(MongoClientWrapper.class);
        verify(mongoFactory).getObject(propertyPrefix);
    }

    @Test
    public void testGetClientWrapperFromDatasource_ExistingDatasource() {
        String datasourceId = "test-datasource";
        String propertyPrefix = MANAGEMENT.getRepositoryPropertyKey();

        setupDefaultConfiguration();

        ClientWrapper<MongoClient> result1 = mongoConnectionProvider.getClientWrapperFromDatasource(datasourceId, propertyPrefix);
        ClientWrapper<MongoClient> result2 = mongoConnectionProvider.getClientWrapperFromDatasource(datasourceId, propertyPrefix);

        assertThat(result1).isSameAs(result2);
        verify(mongoFactory, times(1)).getObject(propertyPrefix);
    }

    @Test
    public void testGetClientWrapperFromDatasource_CleanupOnShutdown() {
        String datasourceId = "test-datasource";
        String propertyPrefix = MANAGEMENT.getRepositoryPropertyKey();

        setupDefaultConfiguration();

        ClientWrapper<MongoClient> result = mongoConnectionProvider.getClientWrapperFromDatasource(datasourceId, propertyPrefix);

        MongoClientWrapper wrapper = (MongoClientWrapper) result;
        wrapper.releaseClient();

        verify(mongoFactory).getObject(propertyPrefix);
    }

    @Test
    public void testStop_WithAllClients() throws Exception {
        setupDefaultConfiguration();
        mongoConnectionProvider.afterPropertiesSet();

        @SuppressWarnings("unchecked")
        ConnectionProvider<MongoClient, MongoConnectionConfiguration> result = mongoConnectionProvider.stop();

        assertThat(result).isSameAs(mongoConnectionProvider);
    }

    @Test
    public void testStop_WithNullClients() throws Exception {
        @SuppressWarnings("unchecked")
        ConnectionProvider<MongoClient, MongoConnectionConfiguration> result = mongoConnectionProvider.stop();

        assertThat(result).isSameAs(mongoConnectionProvider);
    }

    @Test
    public void testCanHandle_MongoBackend() {
        boolean result = mongoConnectionProvider.canHandle(BACKEND_TYPE_MONGO);

        assertThat(result).isTrue();
    }

    @Test
    public void testCanHandle_NonMongoBackend() {
        boolean result = mongoConnectionProvider.canHandle("jdbc");

        assertThat(result).isFalse();
    }

    @Test
    public void testCanHandle_NullBackend() {
        boolean result = mongoConnectionProvider.canHandle(null);

        assertThat(result).isFalse();
    }

    @Test
    public void testCanHandle_EmptyBackend() {
        boolean result = mongoConnectionProvider.canHandle("");

        assertThat(result).isFalse();
    }

    @Test
    public void testMultipleDatasources() {
        String datasource1 = "datasource1";
        String datasource2 = "datasource2";
        String prefix1 = MANAGEMENT.getRepositoryPropertyKey();
        String prefix2 = GATEWAY.getRepositoryPropertyKey();

        setupDefaultConfiguration();

        ClientWrapper<MongoClient> result1 = mongoConnectionProvider.getClientWrapperFromDatasource(datasource1, prefix1);
        ClientWrapper<MongoClient> result2 = mongoConnectionProvider.getClientWrapperFromDatasource(datasource2, prefix2);

        assertThat(result1).isNotSameAs(result2);
        verify(mongoFactory).getObject(prefix1);
        verify(mongoFactory).getObject(prefix2);
    }

    @Test
    public void testDatasourceClientWrapperCleanup() throws Exception {
        String datasourceId = "cleanup-test";
        String propertyPrefix = MANAGEMENT.getRepositoryPropertyKey();

        setupDefaultConfiguration();

        ClientWrapper<MongoClient> result = mongoConnectionProvider.getClientWrapperFromDatasource(datasourceId, propertyPrefix);

        java.lang.reflect.Field dsClientWrappersField = MongoConnectionProvider.class.getDeclaredField("dsClientWrappers");
        dsClientWrappersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ClientWrapper<MongoClient>> dsClientWrappers = (Map<String, ClientWrapper<MongoClient>>) dsClientWrappersField.get(mongoConnectionProvider);

        assertThat(dsClientWrappers).containsKey(datasourceId);

        MongoClientWrapper wrapper = (MongoClientWrapper) result;
        wrapper.releaseClient();
    }

    private void setupDefaultConfiguration() {
        setBooleanProperty(OAUTH2.getRepositoryPropertyKey() + ".use-management-settings", true, true);
        setBooleanProperty(OAUTH2.getRepositoryPropertyKey() + ".use-gateway-settings", false, false);
        setBooleanProperty(GATEWAY.getRepositoryPropertyKey() + ".use-management-settings", true, true);
        setBooleanProperty(MANAGEMENT.getRepositoryPropertyKey() + ".use-management-settings", true, true);
        setBooleanProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".use-management-settings", true, false);
        setBooleanProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".use-gateway-settings", false, false);

        setDefaultDatabaseName();

        when(mongoFactory.getObject(any())).thenReturn(mongoClient);
    }

    private void setDefaultDatabaseName() {
        setStringProperty(OAUTH2.getRepositoryPropertyKey() + ".mongodb.uri", "");
        setStringProperty(GATEWAY.getRepositoryPropertyKey() + ".mongodb.uri", "");
        setStringProperty(MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.uri", "");
        setStringProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".mongodb.uri", "");
    }

    private void setBooleanProperty(String property, Boolean provided, Boolean returned) {
        when(environment.getProperty(eq(property), eq(Boolean.class), eq(provided))).thenReturn(returned);
    }

    private void setStringProperty(String property, String value) {
        when(environment.getProperty(eq(property), eq(value))).thenReturn(value);
    }
}
