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
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(title = "Secret expiration settings", description = "Controls whether client secrets in the domain expire " +
        "and after how long.")
public class SecretExpirationSettings {
    @Schema(description = "Whether client-secret expiration is enabled.")
    private Boolean enabled;
    @Schema(description = "Lifetime, in seconds, of a client secret before it expires.", example = "7776000")
    private Long expiryTimeSeconds;

    public SecretExpirationSettings() {
    }

    public SecretExpirationSettings(SecretExpirationSettings other) {
        this.enabled = other.enabled;
        this.expiryTimeSeconds = other.expiryTimeSeconds;
    }
}
