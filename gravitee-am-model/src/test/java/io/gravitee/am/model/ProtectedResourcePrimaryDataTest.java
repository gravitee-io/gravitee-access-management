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
package io.gravitee.am.model;

import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ClientSecret;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtectedResourcePrimaryDataTest {

    @Test
    void of_shouldRemoveClientSecretFromOAuthSettings() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("id");
        resource.setClientId("client-id");
        resource.setName("name");
        resource.setType(ProtectedResource.Type.MCP_SERVER);
        resource.setResourceIdentifiers(List.of("https://example.com"));
        resource.setUpdatedAt(new Date());

        ApplicationOAuthSettings oauthSettings = new ApplicationOAuthSettings();
        oauthSettings.setClientId("client-id");
        oauthSettings.setClientSecret("super-secret-value");
        oauthSettings.setGrantTypes(List.of("client_credentials"));

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oauthSettings);
        resource.setSettings(settings);

        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setSecret("hashed-secret");
        resource.setClientSecrets(List.of(clientSecret));

        ProtectedResourcePrimaryData result = ProtectedResourcePrimaryData.of(resource);

        assertNull(result.settings().getOauth().getClientSecret(),
                "Client secret must not be exposed in GET responses");
        assertEquals("client-id", result.settings().getOauth().getClientId());
        assertEquals(List.of("client_credentials"), result.settings().getOauth().getGrantTypes());
    }

    @Test
    void of_shouldHandleNullSettings() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("id");
        resource.setClientId("client-id");
        resource.setName("name");
        resource.setType(ProtectedResource.Type.MCP_SERVER);
        resource.setUpdatedAt(new Date());

        ProtectedResourcePrimaryData result = ProtectedResourcePrimaryData.of(resource);

        assertNull(result.settings());
    }

    @Test
    void of_shouldHandleNullOAuthSettings() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("id");
        resource.setClientId("client-id");
        resource.setName("name");
        resource.setType(ProtectedResource.Type.MCP_SERVER);
        resource.setUpdatedAt(new Date());

        ApplicationSettings settings = new ApplicationSettings();
        resource.setSettings(settings);

        ProtectedResourcePrimaryData result = ProtectedResourcePrimaryData.of(resource);

        assertNotNull(result.settings());
        assertNull(result.settings().getOauth());
    }

    @Test
    void of_shouldNotMutateOriginalResourceSettings() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("id");
        resource.setClientId("client-id");
        resource.setName("name");
        resource.setType(ProtectedResource.Type.MCP_SERVER);
        resource.setUpdatedAt(new Date());

        ApplicationOAuthSettings oauthSettings = new ApplicationOAuthSettings();
        oauthSettings.setClientSecret("super-secret-value");

        ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(oauthSettings);
        resource.setSettings(settings);

        ProtectedResourcePrimaryData.of(resource);

        assertEquals("super-secret-value", resource.getSettings().getOauth().getClientSecret(),
                "Original ProtectedResource must not be mutated by sanitization");
    }

    @Test
    void of_shouldNotIncludeClientSecrets() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("id");
        resource.setClientId("client-id");
        resource.setName("name");
        resource.setType(ProtectedResource.Type.MCP_SERVER);
        resource.setUpdatedAt(new Date());

        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setSecret("hashed-secret");
        resource.setClientSecrets(List.of(clientSecret));

        ProtectedResourcePrimaryData result = ProtectedResourcePrimaryData.of(resource);

        // ProtectedResourcePrimaryData record does not have a clientSecrets field at all
        assertEquals("id", result.id());
        assertEquals("client-id", result.clientId());
    }
}