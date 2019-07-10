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
package io.gravitee.am.gateway.handler.oidc.service.clientregistration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.model.Client;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DynamicClientRegistrationTemplate {

    @JsonProperty("software_id")
    private String softwareId;

    @JsonProperty("description")
    private String description;

    public String getSoftwareId() {
        return softwareId;
    }

    public void setSoftwareId(String softwareId) {
        this.softwareId = softwareId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static List<DynamicClientRegistrationTemplate> from(List<Client> templates) {
        if(templates==null) {
            return Collections.emptyList();
        }
        return templates.stream()
                .map(template -> {
                    DynamicClientRegistrationTemplate res = new DynamicClientRegistrationTemplate();
                    res.setSoftwareId(template.getId());
                    res.setDescription(template.getClientName());
                    return res;
                })
                .collect(Collectors.toList());
    }
}
