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

import io.gravitee.am.service.validators.url.Url;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.gravitee.am.service.model.NewProtectedResource.NAME_MAX_LENGTH;

@Getter
@Setter
public class UpdateProtectedResource {

    @NotBlank
    @Size(min = 1, max = NAME_MAX_LENGTH, message = "Name must be between 1 and 64 characters")
    private String name;

    private String description;

    @NotEmpty
    private List<@NotBlank @Url(allowFragment = false) String> resourceIdentifiers;

    private List<@Valid UpdateProtectedResourceFeature> features;

    public List<UpdateProtectedResourceFeature> getFeatures() {
        return Objects.requireNonNullElseGet(features, ArrayList::new);
    }
}

