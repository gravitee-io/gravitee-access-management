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
package io.gravitee.am.model.flow;

import io.gravitee.am.model.ReferenceType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Flow {

    /**
     * Flow technical id
     */
    private String id;
    /**
     * The type of reference the flow is attached to (for now, should be DOMAIN).
     */
    private ReferenceType referenceType;
    /**
     * The id of the reference the flow is attached to (for now, should be the domain id).
     */
    private String referenceId;
    /**
     * The application id the flow is attached to (if flow is defined at application level).
     */
    private String application;
    /**
     * Flow name
     */
    private String name;
    /**
     * execution order of the flow if multiple flows with the same 'type' exist
     */
    private Integer order;
    /**
     * Flow pre steps
     */
    private List<Step> pre = new ArrayList<>();
    /**
     * Flow post steps
     */
    private List<Step> post = new ArrayList<>();
    /**
     * Flow state
     */
    private boolean enabled;
    /**
     * The type of flow
     */
    private Type type;
    /**
     * Condition attached to the Flow
     */
    private String condition;
    /**
     * Flow created date
     */
    private Date createdAt;
    /**
     * Flow updated date
     */
    private Date updatedAt;

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public Flow() {
    }

    public Flow(Flow other) {
        this.id = other.id;
        this.referenceType = other.referenceType;
        this.referenceId = other.referenceId;
        this.application = other.application;
        this.name = other.name;
        this.pre = other.pre;
        this.post = other.post;
        this.enabled = other.enabled;
        this.type = other.type;
        this.order = other.getOrder();
        this.condition = other.condition;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
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

    @Override
    public String toString() {
        return "{\"_class\":\"Flow\", " +
                "\"id\":" + (id == null ? "null" : "\"" + id + "\"") + ", " +
                "\"referenceType\":" + (referenceType == null ? "null" : referenceType) + ", " +
                "\"referenceId\":" + (referenceId == null ? "null" : "\"" + referenceId + "\"") + ", " +
                "\"name\":" + (name == null ? "null" : "\"" + name + "\"") +
                "}";
    }
}
