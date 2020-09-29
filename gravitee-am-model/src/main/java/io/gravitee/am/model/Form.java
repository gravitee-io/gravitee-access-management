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

import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Form {

    private String id;
    private boolean enabled;
    private ReferenceType referenceType;
    private String referenceId;
    private String client;
    private String template;
    private String content;
    private String assets;
    private Date createdAt;
    private Date updatedAt;

    public Form() {
    }

    public Form(boolean enabled, String template) {
        this.enabled = enabled;
        this.template = template;
    }

    public Form(Form other) {
        this.id = other.id;
        this.enabled = other.enabled;
        this.referenceType = other.referenceType;
        this.referenceId = other.referenceId;
        this.client = other.client;
        this.template = other.template;
        this.content = other.content;
        this.assets = other.assets;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAssets() {
        return assets;
    }

    public void setAssets(String assets) {
        this.assets = assets;
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
