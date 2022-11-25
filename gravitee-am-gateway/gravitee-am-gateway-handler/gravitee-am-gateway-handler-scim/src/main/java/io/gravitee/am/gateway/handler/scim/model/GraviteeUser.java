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
import java.util.List;
import java.util.Map;

/**
 * SCIM Gravitee User Resource used to manage custom claims
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeUser extends User {

    public static final List<String> SCHEMAS = Arrays.asList(Schema.SCHEMA_URI_USER, Schema.SCHEMA_URI_CUSTOM_USER);

    @JsonProperty(Schema.SCHEMA_URI_CUSTOM_USER)
    private Map<String, Object> additionalInformation;

    public Map<String, Object> getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(Map<String, Object> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }
}
