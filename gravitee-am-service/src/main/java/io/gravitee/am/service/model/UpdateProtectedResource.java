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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.service.validators.url.Url;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Request body for PUT /protected-resources/{id}.
 * Additional properties (e.g. read-only fields such as createdAt/updatedAt from the GET response) are ignored if sent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class UpdateProtectedResource {

    @NotBlank
    @Size(min = 1, max = NewProtectedResource.NAME_MAX_LENGTH, message = "Name must be between {min} and {max} characters")
    @Pattern(regexp = NewProtectedResource.NAME_PATTERN, message = "Name must begin with a non-whitespace character")
    private String name;

    private String description;

    @NotEmpty
    private List<@NotBlank @Url(allowFragment = false) String> resourceIdentifiers;

    private List<@Valid UpdateProtectedResourceFeature> features;

    private ApplicationSettings settings;
    
    public List<UpdateProtectedResourceFeature> getFeatures() {
        return Objects.requireNonNullElseGet(features, ArrayList::new);
    }
}

