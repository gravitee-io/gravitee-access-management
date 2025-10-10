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

package io.gravitee.am.model.application;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class  ApplicationSecretSettings {

    private String id;

    private String algorithm;

    @JsonIgnore
    private Map<String, Object> properties = new TreeMap<>();

    public ApplicationSecretSettings(String id, String algorithm, Map<String, Object> properties) {
        this.id = id;
        this.algorithm = algorithm;
        this.properties= properties != null ? new TreeMap<>(properties) : new TreeMap<>();
    }

    public ApplicationSecretSettings() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
