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
package io.gravitee.am.service.impl.application;

import io.gravitee.am.common.oauth2.ClientType;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationMCPSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.application.MCPToolDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * MCP (Model Context Protocol) Application Template
 * 
 * @author GraviteeSource Team
 */
public class ApplicationMCPTemplate extends ApplicationAbstractTemplate {

    @Override
    public boolean canHandle(Application application) {
        return ApplicationType.MCP.equals(application.getType());
    }

    @Override
    public void handle(Application application) {
        // assign values
        update(application, false);
    }

    @Override
    public void changeType(Application application) {
        // force default values
        update(application, true);
    }

    private void update(Application application, boolean force) {
        // check for null values
        if (application.getSettings() == null) {
            application.setSettings(new ApplicationSettings());
        }
        if (application.getSettings().getOauth() == null) {
            application.getSettings().setOauth(new ApplicationOAuthSettings());
        }
        if (application.getSettings().getMcp() == null) {
            application.getSettings().setMcp(new ApplicationMCPSettings());
        }

        // assign OAuth values
        ApplicationOAuthSettings oAuthSettings = application.getSettings().getOauth();
        oAuthSettings.setClientId(oAuthSettings.getClientId() == null ? RandomString.generate() : oAuthSettings.getClientId());
        oAuthSettings.setClientSecret(oAuthSettings.getClientSecret() == null ? SecureRandomString.generate() : oAuthSettings.getClientSecret());
        oAuthSettings.setClientName(oAuthSettings.getClientName() == null ? application.getName() : oAuthSettings.getClientName());
        oAuthSettings.setClientType(ClientType.CONFIDENTIAL);
        oAuthSettings.setApplicationType(io.gravitee.am.common.oidc.ApplicationType.WEB);

        if (force || (oAuthSettings.getGrantTypes() == null || oAuthSettings.getGrantTypes().isEmpty())) {
            // MCP applications should have client_credentials flow for server-to-server communication
            oAuthSettings.setGrantTypes(Collections.singletonList(GrantType.CLIENT_CREDENTIALS));
            oAuthSettings.setTokenEndpointAuthMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        }

        // assign MCP values
        ApplicationMCPSettings mcpSettings = application.getSettings().getMcp();
        
        // Set default MCP URL if not provided
        if (mcpSettings.getUrl() == null || mcpSettings.getUrl().trim().isEmpty()) {
            mcpSettings.setUrl("https://api.example.com/mcp");
        }

        // Set default tool definitions if not provided
        if (force || (mcpSettings.getToolDefinitions() == null || mcpSettings.getToolDefinitions().isEmpty())) {
            mcpSettings.setToolDefinitions(createDefaultToolDefinitions());
        }
    }

    private ArrayList<MCPToolDefinition> createDefaultToolDefinitions() {
        ArrayList<MCPToolDefinition> defaultTools = new ArrayList<>();

        return defaultTools;
    }
}
