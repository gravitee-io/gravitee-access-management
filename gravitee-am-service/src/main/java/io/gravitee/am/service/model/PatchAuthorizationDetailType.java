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

import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.service.utils.SetterUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Optional;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
@ToString(exclude = "schema")
public class PatchAuthorizationDetailType {

    @Schema(maxLength = AuthorizationDetailType.NAME_MAX_LENGTH, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Optional<@NotBlank(message = "Name is required")
            @Size(max = AuthorizationDetailType.NAME_MAX_LENGTH, message = "Name must be at most {max} characters") String> name;

    @Schema(maxLength = AuthorizationDetailType.DESCRIPTION_MAX_LENGTH)
    private Optional<@Size(max = AuthorizationDetailType.DESCRIPTION_MAX_LENGTH, message = "Description must be at most {max} characters") String> description;

    @Schema(maxLength = AuthorizationDetailType.SCHEMA_MAX_LENGTH,
            description = "JSON Schema 2020-12 the authorization_details objects of this type must satisfy; "
                    + "a schema without \"$schema\" is read as 2020-12. "
                    + "Its root must be an object whose \"type\" property is constant to this entry's type and required. "
                    + "Only local references (\"#...\") are allowed.")
    private Optional<@Size(max = AuthorizationDetailType.SCHEMA_MAX_LENGTH, message = "Schema must be at most {max} characters") String> schema;

    @Schema(description = "Whether the type is advertised through discovery.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Optional<@NotNull(message = "Discovery can't be cleared") Boolean> discovery;

    @Schema(minimum = "1",
            description = "Expiration time for user consent, in seconds. Absent means the gateway default applies.")
    private Optional<@Min(value = 1, message = "Expires in must be greater than 0") Integer> expiresIn;

    public AuthorizationDetailType patch(AuthorizationDetailType toPatch) {
        AuthorizationDetailType patched = toPatch.toBuilder().build();
        SetterUtils.safeSet(patched::setName, this.getName());
        SetterUtils.safeSet(patched::setDescription, this.getDescription());
        SetterUtils.safeSet(patched::setSchema, this.getSchema());
        SetterUtils.safeSet(patched::setDiscovery, this.getDiscovery(), boolean.class);
        SetterUtils.safeSet(patched::setExpiresIn, this.getExpiresIn());
        return patched;
    }
}
