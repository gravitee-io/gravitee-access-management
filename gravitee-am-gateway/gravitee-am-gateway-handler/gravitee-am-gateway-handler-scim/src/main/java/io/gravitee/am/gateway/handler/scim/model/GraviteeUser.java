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
package io.gravitee.am.gateway.handler.scim.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.common.scim.Schema;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SCIM Gravitee User Resource used to manage custom claims
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeUser extends EnterpriseUser {

    public static final List<String> SCHEMAS = Arrays.asList(Schema.SCHEMA_URI_USER, Schema.SCHEMA_URI_CUSTOM_USER);
    public static final List<String> SCHEMAS_WITH_ENTERPRISE = Arrays.asList(Schema.SCHEMA_URI_USER, Schema.SCHEMA_URI_ENTERPRISE_USER, Schema.SCHEMA_URI_CUSTOM_USER);
    private static final List<String> FORBIDDEN_PROPERTIES = Arrays.asList("password");

    @JsonProperty(Schema.SCHEMA_URI_CUSTOM_USER)
    private Map<String, Object> additionalInformation;

    public Map<String, Object> getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(Map<String, Object> additionalInformation) {
        Map<String, Object> sanitized = additionalInformation == null ? new HashMap<>() : new HashMap<>(additionalInformation);
        FORBIDDEN_PROPERTIES.forEach(sanitized::remove);
        this.additionalInformation = sanitized;
    }
}
