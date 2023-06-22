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
package io.gravitee.am.gateway.handler.common.policy;

import io.gravitee.am.model.uma.policy.AccessPolicy;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultRule implements Rule {

    private String type;
    private String name;
    private String description;
    private boolean enabled;
    private String condition;
    private Map<String, Object> metadata;

    public DefaultRule() { }

    public DefaultRule(String type, String name, String description, boolean enabled, String condition) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.condition = condition;
    }

    public DefaultRule(AccessPolicy accessPolicy) {
        this.type = accessPolicy.getType().getName();
        this.name = accessPolicy.getName();
        this.description = accessPolicy.getDescription();
        this.enabled = accessPolicy.isEnabled();
        this.condition = accessPolicy.getCondition();
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String condition() {
        return condition;
    }

    @Override
    public Map<String, Object> metadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
