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
package io.gravitee.am.management.handlers.management.api.model;

import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.model.flow.Type;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FlowEntity {

    private String id;
    private String name;
    private List<Step> pre;
    private List<Step> post;
    private boolean enabled;
    private Type type;
    private String condition;
    private String icon;
    @Schema(type = "java.lang.Long")
    private Date createdAt;
    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public FlowEntity() { }

    public FlowEntity(Flow other) {
        this.id = other.getId();
        this.name = other.getName();
        this.pre = other.getPre();
        this.post = other.getPost();
        this.enabled = other.isEnabled();
        this.type = other.getType();
        this.icon = getIcon(other.getType());
        this.condition = other.getCondition();
        this.createdAt = other.getCreatedAt();
        this.updatedAt = other.getUpdatedAt();
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

    public List<Step> getPre() {
        return pre;
    }

    public void setPre(List<Step> pre) {
        this.pre = pre;
    }

    public List<Step> getPost() {
        return post;
    }

    public void setPost(List<Step> post) {
        this.post = post;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
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

    private static String getIcon(Type type) {
        if (type == null) {
            return "communication:shield-thunder";
        }

        return switch (type) {
            case ROOT -> "home:earth";
            case LOGIN_IDENTIFIER -> "code:right-circle";
            case LOGIN -> "finance:file";
            case CONSENT -> "general:shield-check";
            case REGISTER, MFA_ENROLLMENT, MFA_CHALLENGE -> "communication:shield-user";
            case RESET_PASSWORD -> "finance:protected-file";
            case REGISTRATION_CONFIRMATION -> "communication:clipboard-check";
            case TOKEN -> "shopping:ticket";
            case CONNECT -> "general:scale";
            case WEBAUTHN_REGISTER -> "action:fingerprint";
            default -> "communication:shield-thunder";
        };
    }
}
