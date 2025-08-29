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
package io.gravitee.am.repository.mongodb;

import io.gravitee.am.repository.mongodb.gateway.GatewayRepositoryConfiguration;
import io.gravitee.am.repository.mongodb.management.ManagementRepositoryConfiguration;
import io.gravitee.am.repository.mongodb.oauth2.OAuth2RepositoryConfiguration;
import io.gravitee.platform.repository.api.Scope;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertArrayEquals;

/**
 * @author GraviteeSource Team
 */
public class MongoRepositoryProviderTest {

    private final MongoRepositoryProvider repositoryProvider = new MongoRepositoryProvider();

    @Test
    public void shouldReturnMongoDbType() {
        String type = repositoryProvider.type();

        assertEquals("mongodb", type);
    }

    @Test
    public void shouldReturnAllSupportedScopes() {
        Scope[] scopes = repositoryProvider.scopes();

        Scope[] expectedScopes = {Scope.MANAGEMENT, Scope.OAUTH2, Scope.GATEWAY};
        assertArrayEquals(expectedScopes, scopes);
    }

    @Test
    public void shouldReturnManagementConfigurationForManagementScope() {
        Class<?> configuration = repositoryProvider.configuration(Scope.MANAGEMENT);

        assertEquals(ManagementRepositoryConfiguration.class, configuration);
    }

    @Test
    public void shouldReturnOAuth2ConfigurationForOAuth2Scope() {
        Class<?> configuration = repositoryProvider.configuration(Scope.OAUTH2);

        assertEquals(OAuth2RepositoryConfiguration.class, configuration);
    }

    @Test
    public void shouldReturnGatewayConfigurationForGatewayScope() {
        Class<?> configuration = repositoryProvider.configuration(Scope.GATEWAY);

        assertEquals(GatewayRepositoryConfiguration.class, configuration);
    }
}
