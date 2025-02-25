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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class UpdateIdentityProvider {

    @NotNull
    private String name;

    @NotBlank
    private String type;

    @NotNull
    private String configuration;

    private Map<String, String> mappers;

    private Map<String, String[]> roleMapper;

    private Map<String, String[]> groupMapper;

    private List<String> domainWhitelist;

    private String passwordPolicy;

    @Override
    public String toString() {
        return "UpdateIdentityProvider{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
