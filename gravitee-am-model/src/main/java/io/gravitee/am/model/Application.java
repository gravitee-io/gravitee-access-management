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

import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Application {

    /**
     * Application technical id
     */
    private String id;
    /**
     * Application name
     */
    private String name;
    /**
     * Application type
     */
    private ApplicationType type;
    /**
     * Application description
     */
    private String description;
    /**
     * Security domain associated to the application
     */
    private String domain;
    /**
     * Application state
     */
    private boolean enabled = true;
    /**
     * Boolean value specifying whether the application should be use as a registration template or active application
     */
    private boolean template;
    /**
     * Identity providers used for authentication
     */
    private Set<String> identities;
    /**
     * Certificate use to sign the tokens
     */
    private String certificate;
    /**
     * Application metadata
     */
    private Map<String, Object> metadata;
    /**
     * Application settings
     */
    private ApplicationSettings settings;
    /**
     * Application created date
     */
    private Date createdAt;
    /**
     * Application updated date
     */
    private Date updatedAt;

    public Application() {
    }

    public Application(Application other) {
        this.id = other.id;
        this.name = other.name;
        this.type = other.type;
        this.description = other.description;
        this.domain = other.domain;
        this.enabled = other.enabled;
        this.template = other.template;
        this.identities = other.identities;
        this.certificate = other.certificate;
        this.metadata = other.metadata != null ? new HashMap<>(other.metadata) : null;
        this.settings = other.settings != null ? new ApplicationSettings(other.settings) : null;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

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

    public ApplicationType getType() {
        return type;
    }

    public void setType(ApplicationType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public ApplicationSettings getSettings() {
        return settings;
    }

    public void setSettings(ApplicationSettings settings) {
        this.settings = settings;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
