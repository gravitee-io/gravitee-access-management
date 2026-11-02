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
package io.gravitee.am.model.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "Application authorization detail type settings")
public class ApplicationAuthorizationDetailTypeSettings {

    @Schema(description = "The type of one of the domain's authorization detail types, matched exactly, case included. "
            + "Unique within the list.",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "payment_initiation")
    private String type;

    @Schema(description = "Consent expiry in seconds for this application. When absent, the authorization detail type's own "
            + "expiresIn applies, then the gateway default.", minimum = "1")
    private Integer expiresIn;

    public ApplicationAuthorizationDetailTypeSettings(String type) {
        this.type = type;
    }
}
