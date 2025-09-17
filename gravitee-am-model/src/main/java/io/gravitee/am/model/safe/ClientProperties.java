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
package io.gravitee.am.model.safe;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.CookieSettings;
import io.gravitee.am.model.application.ApplicationMCPSettings;
import io.gravitee.am.model.oidc.Client;

import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientProperties {

    private String id;
    private String domain;
    private String clientId;
    private String clientName;
    private String name;
    private CookieSettings cookieSettings;
    private Map<String, Object> metadata;
    private McpProperties mcp;

    public ClientProperties() {
    }

    public ClientProperties(Client client) {
        this.id = client.getId();
        this.domain = client.getDomain();
        this.clientId = client.getClientId();
        this.clientName = client.getClientName();
        this.name = client.getClientName();
        this.cookieSettings = client.getCookieSettings();
        this.metadata = client.getMetadata() == null ? new HashMap<>() : new HashMap<>(client.getMetadata());
        this.mcp = mcpProperties(client.getMcp());
    }

    private McpProperties mcpProperties(ApplicationMCPSettings mcp) {
        if(mcp == null) {
            return null;
        }
        McpProperties mcpProperties = new McpProperties();
        List<String> tools = new ArrayList<>();
        Set<String> scopes = new HashSet<>();
        mcp.getToolDefinitions().forEach(toolDefinition -> {
            scopes.addAll(toolDefinition.getRequiredScopes());
            tools.add(toolDefinition.getName());
        });
        mcpProperties.setTools(tools);
        mcpProperties.setScopes(List.copyOf(scopes));
        return mcpProperties;
    }

    public ClientProperties(Application app) {
        this(app.toClient());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CookieSettings getCookieSettings() {
        return cookieSettings;
    }

    public void setCookieSettings(CookieSettings cookieSettings) {
        this.cookieSettings = cookieSettings;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public McpProperties getMcp() {
        return mcp;
    }

    public void setMcp(McpProperties mcp) {
        this.mcp = mcp;
    }
}
