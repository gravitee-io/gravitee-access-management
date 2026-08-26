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

import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.gravitee.am.service.spring.datasource.DataSourcesConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers how a Mongo identity provider picks the cluster it talks to, in particular the data plane
 * branch a domain's default identity provider relies on.
 *
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MongoAbstractProviderTest {

    private static final String DATA_PLANE_ID = "dp1";

    /** Concrete class for connection wiring tests. */
    private static class TestMongoProvider extends MongoAbstractProvider {
    }

    @Mock
    private ConnectionProvider commonConnectionProvider;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private DataPlaneProvider dataPlaneProvider;

    @Mock
    private ClientWrapper dataPlaneClientWrapper;

    @Mock
    private ClientWrapper commonClientWrapper;

    @Mock
    private ClientWrapper datasourceClientWrapper;

    @Mock
    private DataSourcesConfiguration dataSourcesConfiguration;

    private final MockEnvironment environment = new MockEnvironment();
    private final MongoIdentityProviderConfiguration configuration = new MongoIdentityProviderConfiguration();
    private final IdentityProvider identityProviderEntity = new IdentityProvider();

    private TestMongoProvider provider;

    @Before
    public void setUp() {
        provider = new TestMongoProvider();
        ReflectionTestUtils.setField(provider, "commonConnectionProvider", commonConnectionProvider);
        ReflectionTestUtils.setField(provider, "dataPlaneRegistry", dataPlaneRegistry);
        ReflectionTestUtils.setField(provider, "identityProviderEntity", identityProviderEntity);
        ReflectionTestUtils.setField(provider, "configuration", configuration);
        ReflectionTestUtils.setField(provider, "environment", environment);
        ReflectionTestUtils.setField(provider, "dataSourcesConfiguration", dataSourcesConfiguration);

        identityProviderEntity.setSystem(true);
        identityProviderEntity.setDataPlaneId(DATA_PLANE_ID);
    }

    @Test
    public void systemProviderBoundToTheSystemCluster_usesTheDataPlaneClient() {
        environment.setProperty("repositories.system-cluster", "gateway");
        configuration.setUseSystemCluster(true);
        when(dataPlaneRegistry.getProviderById(DATA_PLANE_ID)).thenReturn(dataPlaneProvider);
        when(dataPlaneProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(dataPlaneProvider.getClientWrapper()).thenReturn(dataPlaneClientWrapper);

        provider.afterPropertiesSet();

        Assert.assertSame(dataPlaneClientWrapper, ReflectionTestUtils.getField(provider, "clientWrapper"));
        verify(commonConnectionProvider, never()).getClientWrapper();
    }

    @Test
    public void systemProviderBoundToTheSystemCluster_readsTheDataPlaneDatabase() {
        environment.setProperty("repositories.system-cluster", "gateway");
        configuration.setUseSystemCluster(true);
        // the value the management API persisted, which must not be used against the data plane cluster
        configuration.setDatabase("management-db");
        when(dataPlaneRegistry.getProviderById(DATA_PLANE_ID)).thenReturn(dataPlaneProvider);
        when(dataPlaneProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(dataPlaneProvider.getClientWrapper()).thenReturn(dataPlaneClientWrapper);
        when(dataPlaneClientWrapper.getDatabaseName()).thenReturn("data-plane-db");

        provider.afterPropertiesSet();

        // client and database both come from the data plane, so they cannot disagree
        Assert.assertEquals("data-plane-db", configuration.getDatabase());
    }

    @Test
    public void nonSystemProviderOnTheDataPlane_keepsItsConfiguredDatabase() {
        // a user-configured provider owns its connection settings: opting into the system cluster must
        // not silently repoint it at another database
        environment.setProperty("repositories.system-cluster", "gateway");
        identityProviderEntity.setSystem(false);
        configuration.setUseSystemCluster(true);
        configuration.setDatabase("my-own-db");
        when(dataPlaneRegistry.getProviderById(DATA_PLANE_ID)).thenReturn(dataPlaneProvider);
        when(dataPlaneProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(dataPlaneProvider.getClientWrapper()).thenReturn(dataPlaneClientWrapper);

        provider.afterPropertiesSet();

        Assert.assertSame(dataPlaneClientWrapper, ReflectionTestUtils.getField(provider, "clientWrapper"));
        Assert.assertEquals("my-own-db", configuration.getDatabase());
    }

    @Test
    public void unboundSystemProvider_keepsItsConfiguredDatabase() {
        environment.setProperty("repositories.system-cluster", "gateway");
        configuration.setUseSystemCluster(false);
        configuration.setDatabase("management-db");
        when(commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(commonConnectionProvider.getClientWrapper()).thenReturn(commonClientWrapper);

        provider.afterPropertiesSet();

        Assert.assertEquals("management-db", configuration.getDatabase());
    }

    @Test
    public void systemProviderNotBoundToTheSystemCluster_usesTheManagementClient() {
        environment.setProperty("repositories.system-cluster", "gateway");
        configuration.setUseSystemCluster(false);
        when(commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(commonConnectionProvider.getClientWrapper()).thenReturn(commonClientWrapper);

        provider.afterPropertiesSet();

        Assert.assertSame(commonClientWrapper, ReflectionTestUtils.getField(provider, "clientWrapper"));
        verify(dataPlaneRegistry, never()).getProviderById(DATA_PLANE_ID);
    }

    @Test
    public void systemProviderOnTheManagementScope_usesTheManagementClient() {
        // the flag alone is not enough: gravitee.yml still has to select the gateway scope
        configuration.setUseSystemCluster(true);
        when(commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(commonConnectionProvider.getClientWrapper()).thenReturn(commonClientWrapper);

        provider.afterPropertiesSet();

        Assert.assertSame(commonClientWrapper, ReflectionTestUtils.getField(provider, "clientWrapper"));
        verify(dataPlaneRegistry, never()).getProviderById(DATA_PLANE_ID);
    }

    @Test
    public void systemProviderWithoutDataPlane_usesTheManagementClient() {
        environment.setProperty("repositories.system-cluster", "gateway");
        configuration.setUseSystemCluster(true);
        identityProviderEntity.setDataPlaneId(null);
        when(commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(commonConnectionProvider.getClientWrapper()).thenReturn(commonClientWrapper);

        provider.afterPropertiesSet();

        Assert.assertSame(commonClientWrapper, ReflectionTestUtils.getField(provider, "clientWrapper"));
    }

    @Test
    public void invalidSystemClusterScope_isRejected() {
        environment.setProperty("repositories.system-cluster", "oauth2");
        lenient().when(commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);

        IllegalStateException error = Assert.assertThrows(IllegalStateException.class, () -> provider.afterPropertiesSet());
        Assert.assertTrue(error.getMessage().contains("repositories.system-cluster"));
    }

    @Test
    public void restrictedProvider_readsTheDatabaseFromItsClientWrapper() {
        environment.setProperty("repositories.system-cluster-idp.pin-database", "true");
        identityProviderEntity.setSystem(false);
        identityProviderEntity.setDataPlaneId(null);
        identityProviderEntity.setSystemClusterRestricted(true);
        configuration.setUseSystemCluster(true);
        configuration.setDatabase("configured-db");
        when(commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(commonConnectionProvider.getClientWrapper(Scope.MANAGEMENT.getName())).thenReturn(commonClientWrapper);
        when(commonClientWrapper.getDatabaseName()).thenReturn("gravitee-am");

        provider.afterPropertiesSet();

        Assert.assertEquals("gravitee-am", configuration.getDatabase());
    }

    @Test
    public void restrictedProvider_keepsItsDatabaseWhenOnlyTheCollectionRuleIsOn() {
        environment.setProperty("repositories.system-cluster-idp.pin-database", "false");
        environment.setProperty("repositories.system-cluster-idp.prefix-users-collection", "true");
        identityProviderEntity.setSystem(false);
        identityProviderEntity.setDataPlaneId(null);
        identityProviderEntity.setSystemClusterRestricted(true);
        configuration.setUseSystemCluster(true);
        configuration.setDatabase("configured-db");
        when(commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(commonConnectionProvider.getClientWrapper(Scope.MANAGEMENT.getName())).thenReturn(commonClientWrapper);

        provider.afterPropertiesSet();

        Assert.assertEquals("configured-db", configuration.getDatabase());
    }

    @Test
    public void providerCreatedBeforeTheRestriction_keepsItsConfiguredDatabase() {
        identityProviderEntity.setSystem(false);
        identityProviderEntity.setDataPlaneId(null);
        identityProviderEntity.setSystemClusterRestricted(false);
        configuration.setUseSystemCluster(true);
        configuration.setDatabase("configured-db");
        when(commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(commonConnectionProvider.getClientWrapper(Scope.MANAGEMENT.getName())).thenReturn(commonClientWrapper);

        provider.afterPropertiesSet();

        Assert.assertEquals("configured-db", configuration.getDatabase());
    }

    @Test
    public void restrictedProviderNamingADatasource_keepsTheDatasourceDatabase() {
        identityProviderEntity.setSystem(false);
        identityProviderEntity.setDataPlaneId(null);
        identityProviderEntity.setSystemClusterRestricted(true);
        configuration.setUseSystemCluster(true);
        configuration.setDatabase("configured-db");
        configuration.setDatasourceId("ds-1");
        environment.setProperty("datasources.ds1.settings.dbname", "datasource-db");
        when(commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)).thenReturn(true);
        when(dataSourcesConfiguration.getDataSourceKeyById("ds-1")).thenReturn("datasources.ds1");
        when(commonConnectionProvider.getClientWrapperFromDatasource("ds-1", "datasources.ds1.settings."))
                .thenReturn(datasourceClientWrapper);

        provider.afterPropertiesSet();

        Assert.assertEquals("datasource-db", configuration.getDatabase());
    }
}
