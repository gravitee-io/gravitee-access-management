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
package io.gravitee.am.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(title = "ID-JAG settings", description = "ID-JAG issuance behavior of token exchange.")
public class IdJagSettings {

    @Schema(description = "Lax validation: also accept an access token issued to the requesting client as the " +
            "subject token. By default only an ID token is accepted.", defaultValue = "false")
    private boolean laxValidation = false;

    public IdJagSettings(IdJagSettings other) {
        this.laxValidation = other.laxValidation;
    }
}
