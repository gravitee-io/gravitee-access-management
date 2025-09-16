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
package io.gravitee.am.service.application;

import io.gravitee.am.common.oauth2.ClientType;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oidc.ApplicationType;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationMCPSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.MCPToolDefinition;
import io.gravitee.am.service.impl.application.ApplicationMCPTemplate;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author GraviteeSource Team
 */
public class ApplicationMCPTemplateTest {

    private ApplicationMCPTemplate applicationMCPTemplate = new ApplicationMCPTemplate();

    @Test
    public void shouldApply_noExistingSettings() {
        Application application = new Application();
        application.setType(io.gravitee.am.model.application.ApplicationType.MCP);
        application.setName("mcp-app");

        applicationMCPTemplate.handle(application);

        Assert.assertNotNull(application);
        Assert.assertNotNull(application.getSettings());
        Assert.assertNotNull(application.getSettings().getOauth());
        Assert.assertNotNull(application.getSettings().getMcp());

        // Test OAuth settings
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        Assert.assertTrue(oAuthSettings.getClientId() != null && !oAuthSettings.getClientId().isEmpty());
        Assert.assertTrue(oAuthSettings.getClientSecret() != null && !oAuthSettings.getClientSecret().isEmpty());
        Assert.assertEquals("mcp-app", oAuthSettings.getClientName());
        Assert.assertEquals(ClientType.CONFIDENTIAL, oAuthSettings.getClientType());
        Assert.assertEquals(io.gravitee.am.model.application.ApplicationType.WEB.toString().toLowerCase(), oAuthSettings.getApplicationType());
        Assert.assertEquals(Collections.singletonList(GrantType.CLIENT_CREDENTIALS), oAuthSettings.getGrantTypes());
        Assert.assertNull(oAuthSettings.getResponseTypes());
        Assert.assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, oAuthSettings.getTokenEndpointAuthMethod());

        // Test MCP settings
        ApplicationMCPSettings mcpSettings = application.getSettings().getMcp();
        Assert.assertEquals("https://api.example.com/mcp", mcpSettings.getUrl());
        Assert.assertNotNull(mcpSettings.getToolDefinitions());
    }

    @Test
    public void shouldApply_existingOAuthSettings() {
        Application application = new Application();
        application.setType(io.gravitee.am.model.application.ApplicationType.MCP);
        application.setName("mcp-app");

        // Pre-configure some OAuth settings
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientId("existing-client-id");
        oauth.setClientSecret("existing-client-secret");
        oauth.setClientName("existing-name");
        oauth.setGrantTypes(Arrays.asList(GrantType.AUTHORIZATION_CODE));
        settings.setOauth(oauth);
        application.setSettings(settings);

        applicationMCPTemplate.handle(application);

        // OAuth settings should be preserved
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        Assert.assertEquals("existing-client-id", oAuthSettings.getClientId());
        Assert.assertEquals("existing-client-secret", oAuthSettings.getClientSecret());
        Assert.assertEquals("existing-name", oAuthSettings.getClientName());
        Assert.assertEquals(Arrays.asList(GrantType.AUTHORIZATION_CODE), oAuthSettings.getGrantTypes());

        // MCP settings should still be added
        Assert.assertNotNull(application.getSettings().getMcp());
        Assert.assertEquals("https://api.example.com/mcp", application.getSettings().getMcp().getUrl());
    }

    @Test
    public void shouldApply_existingMCPSettings() {
        Application application = new Application();
        application.setType(io.gravitee.am.model.application.ApplicationType.MCP);
        application.setName("mcp-app");

        // Pre-configure MCP settings
        ApplicationSettings settings = new ApplicationSettings();
        ApplicationMCPSettings mcp = new ApplicationMCPSettings();
        mcp.setUrl("https://custom-mcp.example.com");
        mcp.setToolDefinitions(Collections.emptyList());
        settings.setMcp(mcp);
        application.setSettings(settings);

        applicationMCPTemplate.handle(application);

        // MCP settings should be preserved
        ApplicationMCPSettings mcpSettings = application.getSettings().getMcp();
        Assert.assertEquals("https://custom-mcp.example.com", mcpSettings.getUrl());
        Assert.assertEquals(Collections.emptyList(), mcpSettings.getToolDefinitions());
    }
}
