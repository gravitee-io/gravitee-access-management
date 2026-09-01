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
package io.gravitee.am.service.idp;

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemClusterDatabaseResolverTest {

    private static final DataPlaneDescription DEFAULT_PLANE =
            new DataPlaneDescription("default", "Legacy domains", "mongodb", "dataPlanes[0]", null);
    private static final DataPlaneDescription DP1 =
            new DataPlaneDescription("dp1", "dp1", "mongodb", "dataPlanes[1]", null);

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    private final MockEnvironment environment = new MockEnvironment();

    @BeforeEach
    void setUp() {
        environment.setProperty("repositories.management.mongodb.uri", "mongodb://mongodb:27017/management-db");
        environment.setProperty("dataPlanes[0].mongodb.uri", "mongodb://mongodb:27017/dp0-db");
        environment.setProperty("dataPlanes[1].mongodb.uri", "mongodb://mongodb:27017/dp1-db");
        when(dataPlaneRegistry.getDataPlanes()).thenReturn(List.of(DEFAULT_PLANE, DP1));
    }

    @Test
    void should_read_the_management_block_when_the_system_cluster_is_management() {
        assertEquals("management-db", resolver().resolve(idpOnDataPlane("dp1")));
        verify(dataPlaneRegistry, never()).getDataPlanes();
    }

    @Test
    void should_read_the_data_plane_of_the_identity_provider_when_the_system_cluster_is_the_gateway() {
        // The gateway serves the provider from the data plane of its domain, which is the store the
        // management block does not name as soon as several data planes are declared.
        environment.setProperty("repositories.system-cluster", "gateway");

        assertEquals("dp1-db", resolver().resolve(idpOnDataPlane("dp1")));
    }

    @Test
    void should_read_the_default_data_plane_when_the_identity_provider_names_none() {
        environment.setProperty("repositories.system-cluster", "gateway");

        assertEquals("dp0-db", resolver().resolve(idpOnDataPlane(null)));
    }

    @Test
    void should_fall_back_to_the_dbname_of_the_data_plane_when_its_uri_carries_no_database() {
        environment.setProperty("repositories.system-cluster", "gateway");
        environment.setProperty("dataPlanes[1].mongodb.uri", "mongodb://mongodb:27017");
        environment.setProperty("dataPlanes[1].mongodb.dbname", "dp1-db");

        assertEquals("dp1-db", resolver().resolve(idpOnDataPlane("dp1")));
    }

    @Test
    void should_fall_back_to_the_scope_settings_when_the_data_plane_is_unknown() {
        environment.setProperty("repositories.system-cluster", "gateway");

        assertEquals("management-db", resolver().resolve(idpOnDataPlane("gone")));
    }

    @Test
    void should_fall_back_to_the_scope_settings_when_the_data_plane_is_not_a_mongo_one() {
        environment.setProperty("repositories.system-cluster", "gateway");
        when(dataPlaneRegistry.getDataPlanes())
                .thenReturn(List.of(new DataPlaneDescription("dp1", "dp1", "jdbc", "dataPlanes[1]", null)));

        assertEquals("management-db", resolver().resolve(idpOnDataPlane("dp1")));
    }

    @Test
    void should_name_no_database_when_the_system_cluster_is_jdbc() {
        environment.setProperty("repositories.management.type", "jdbc");

        assertNull(resolver().resolve(idpOnDataPlane("dp1")));
    }

    @Test
    void should_name_the_packaged_default_when_nothing_is_configured() {
        var bare = new SystemClusterDatabaseResolver(new RepositoriesEnvironment(new MockEnvironment()), dataPlaneRegistry);

        assertEquals(SystemClusterDatabaseResolver.DEFAULT_DATABASE, bare.resolve(idpOnDataPlane("dp1")));
    }

    private SystemClusterDatabaseResolver resolver() {
        return new SystemClusterDatabaseResolver(new RepositoriesEnvironment(environment), dataPlaneRegistry);
    }

    private IdentityProvider idpOnDataPlane(String dataPlaneId) {
        var idp = new IdentityProvider();
        idp.setId("idp-1");
        idp.setType(SystemClusterIdpPolicy.MONGO_IDP_TYPE);
        idp.setDataPlaneId(dataPlaneId);
        return idp;
    }
}
