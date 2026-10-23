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
import io.gravitee.am.service.validators.authorizationdetailtype.AuthorizationDetailTypeValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
@ToString(exclude = "schema")
public class NewAuthorizationDetailType {

    @Schema(description = "Value of the \"type\" member of the authorization_details objects this entry describes. "
            + "Unique within the domain, ignoring case.",
            example = "payment_initiation", pattern = AuthorizationDetailType.TYPE_PATTERN, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Type is required")
    @Size(max = AuthorizationDetailType.TYPE_MAX_LENGTH, message = "Type must be at most {max} characters")
    @Pattern(regexp = AuthorizationDetailType.TYPE_PATTERN, message = AuthorizationDetailTypeValidator.TYPE_CHARACTERS_MESSAGE)
    private String type;

    @Schema(example = "Payment initiation", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Name is required")
    @Size(max = AuthorizationDetailType.NAME_MAX_LENGTH, message = "Name must be at most {max} characters")
    private String name;

    @Size(max = AuthorizationDetailType.DESCRIPTION_MAX_LENGTH, message = "Description must be at most {max} characters")
    private String description;

    @Schema(description = "JSON Schema 2020-12 the authorization_details objects of this type must satisfy; "
            + "a schema without \"$schema\" is read as 2020-12. "
            + "Its root must be an object whose \"type\" property is constant to this entry's type and required. "
            + "Only local references (\"#...\") are allowed.")
    @Size(max = AuthorizationDetailType.SCHEMA_MAX_LENGTH, message = "Schema must be at most {max} characters")
    private String schema;

    @Schema(description = "Whether the type is advertised through discovery.", defaultValue = "false")
    private boolean discovery;

    @Schema(description = "Expiration time for user consent, in seconds. Absent means the gateway default applies.")
    @Min(value = 1, message = "Expires in must be greater than 0")
    private Integer expiresIn;
}
