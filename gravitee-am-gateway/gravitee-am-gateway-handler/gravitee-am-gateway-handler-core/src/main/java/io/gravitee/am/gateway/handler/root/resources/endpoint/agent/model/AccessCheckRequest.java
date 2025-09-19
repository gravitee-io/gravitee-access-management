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
package io.gravitee.am.gateway.handler.root.resources.endpoint.agent.model;

import java.util.Map;

/**
 * @author GraviteeSource Team
 */
public class AccessCheckRequest {

    private String user;      // e.g. "user:alice"
    private String relation;  // e.g. "can_access"
    private String object;    // e.g. "tool:hammer"
    private Map<String, Object> context; // optional for MCP (tenant, trace, etc.)

    public AccessCheckRequest() {
    }

    public AccessCheckRequest(String user, String relation, String object, Map<String, Object> context) {
        this.user = user;
        this.relation = relation;
        this.object = object;
        this.context = context;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
}