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
package io.gravitee.am.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload of the data plane provisioning endpoint. The organization and the environment are both
 * optional: an id wins, an hrid is looked up when no id is given, and {@code DEFAULT} is used when
 * neither is.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class NewDataPlaneDefinition {

    private String id;

    private String name;

    private String type;

    private String gatewayUrl;

    private String organizationId;

    private String organizationHrid;

    private String environmentId;

    private String environmentHrid;

    /**
     * The {@code dataPlanes[i]} body as it would appear in the gravitee.yml, e.g.
     * <pre>{"mongodb": {"dbname": "gravitee-am-acme", "host": "mongo", "port": 27017}}</pre>
     */
    private JsonNode configuration;

    @Override
    public String toString() {
        return "NewDataPlaneDefinition{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", environmentId='" + environmentId + '\'' +
                '}';
    }
}
