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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.repository.mongodb.common.model.Auditable;
import org.bson.Document;

import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationMongo extends Auditable {

    private String id;
    private String name;
    private String type;
    private String description;
    private String agentCardUrl;
    private String domain;
    private boolean enabled = true;
    private boolean template;
    private Set<String> identities;
    private Set<String> factors;
    private String certificate;
    private Document metadata;
    private ApplicationSettingsMongo settings;
    private Set<ApplicationIdentityProviderMongo> identityProviders;
    private List<ApplicationSecretSettingsMongo> secretSettings;
    private List<ClientSecretMongo> secrets;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAgentCardUrl() {
        return agentCardUrl;
    }

    public void setAgentCardUrl(String agentCardUrl) {
        this.agentCardUrl = agentCardUrl;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTemplate() {
        return template;
    }

    public void setTemplate(boolean template) {
        this.template = template;
    }

    public Set<String> getIdentities() {
        return identities;
    }

    public void setIdentities(Set<String> identities) {
        this.identities = identities;
    }

    public Set<String> getFactors() {
        return factors;
    }

    public void setFactors(Set<String> factors) {
        this.factors = factors;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public Document getMetadata() {
        return metadata;
    }

    public void setMetadata(Document metadata) {
        this.metadata = metadata;
    }

    public ApplicationSettingsMongo getSettings() {
        return settings;
    }

    public void setSettings(ApplicationSettingsMongo settings) {
        this.settings = settings;
    }

    public Set<ApplicationIdentityProviderMongo> getIdentityProviders() {
        return identityProviders;
    }

    public void setIdentityProviders(Set<ApplicationIdentityProviderMongo> identityProviders) {
        this.identityProviders = identityProviders;
    }

    public List<ApplicationSecretSettingsMongo> getSecretSettings() {
        return secretSettings;
    }

    public void setSecretSettings(List<ApplicationSecretSettingsMongo> secretSettings) {
        this.secretSettings = secretSettings;
    }

    public List<ClientSecretMongo> getSecrets() {
        return secrets;
    }

    public void setSecrets(List<ClientSecretMongo> secrets) {
        this.secrets = secrets;
    }
}
