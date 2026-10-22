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
package io.gravitee.am.model.oauth2;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * An RFC 9396 authorization details type recognized by a domain. Its name, description and schema are
 * versioned as {@link AuthorizationDetailTypeVersion}s.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationDetailType {

    public static final int TYPE_MAX_LENGTH = 512;

    // RFC 9396 type characters: visible ASCII (%x21-7E)
    public static final String TYPE_PATTERN = "^[\\x21-\\x7E]*$";

    public static final int NAME_MAX_LENGTH = 255;

    public static final int DESCRIPTION_MAX_LENGTH = 1024;

    public static final int SCHEMA_MAX_LENGTH = 65_536;

    private String id;

    private String domain;

    @Schema(description = "Value of the \"type\" member of the authorization_details objects this entry describes. "
            + "Unique within the domain, ignoring case.",
            example = "payment_initiation", maxLength = TYPE_MAX_LENGTH, pattern = TYPE_PATTERN)
    private String type;

    @Schema(maxLength = NAME_MAX_LENGTH)
    private String name;

    @Schema(maxLength = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Schema(description = "JSON Schema (Draft 7) the authorization_details objects of this type must satisfy, "
            + "returned exactly as submitted.", maxLength = SCHEMA_MAX_LENGTH)
    private String schema;

    @Schema(description = "Whether the type is advertised through discovery.")
    private boolean discovery;

    @Schema(description = "Expiration time for user consent, in seconds. Absent means the gateway default applies.")
    private Integer expiresIn;

    @Schema(description = "Version of the current name, description and schema, starting at 1.")
    private int version;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;
}
