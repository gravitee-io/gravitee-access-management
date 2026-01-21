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

import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.oidc.Client;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class ProtectedResource {

    public enum Type {
        MCP_SERVER;

        public static Type fromString(String type) {
            try {
                return Type.valueOf(type.toUpperCase());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid protected resource type: " + type);
            }
        }
    }

    private String id;

    private String name;

    private String clientId;

    private String domainId;

    private String description;

    private Type type = Type.MCP_SERVER;

    private List<String> resourceIdentifiers;

    private List<ClientSecret> clientSecrets;

    private List<ApplicationSecretSettings> secretSettings;

    private ApplicationSettings settings;

    private List<? extends ProtectedResourceFeature> features;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public ProtectedResource() {
    }

    public ProtectedResource(ProtectedResource protectedResource) {
        this.id = protectedResource.getId();
        this.name = protectedResource.getName();
        this.clientId = protectedResource.getClientId();
        this.domainId = protectedResource.getDomainId();
        this.description = protectedResource.getDescription();
        this.resourceIdentifiers = protectedResource.getResourceIdentifiers();
        this.clientSecrets = protectedResource.getClientSecrets();
        this.settings = protectedResource.getSettings();
        this.secretSettings = protectedResource.getSecretSettings();
        this.createdAt = protectedResource.getCreatedAt();
        this.updatedAt = protectedResource.getUpdatedAt();
        this.features = protectedResource.getFeatures();
    }

    public Client toClient() {
        Client client = new Client();
        client.setId(this.id);
        client.setClientId(this.clientId);
        client.setClientName(this.name);
        client.setDomain(this.domainId);
        client.setEnabled(true);
        client.setSecretSettings(this.secretSettings);
        client.setClientSecrets(this.clientSecrets);
        client.setCreatedAt(this.createdAt);
        client.setUpdatedAt(this.updatedAt);
        return client;
    }
}
