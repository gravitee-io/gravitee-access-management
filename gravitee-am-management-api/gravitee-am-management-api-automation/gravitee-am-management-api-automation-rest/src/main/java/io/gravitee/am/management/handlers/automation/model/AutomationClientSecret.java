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
package io.gravitee.am.management.handlers.automation.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.service.ApplicationSecretService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * The Automation API representation of an application client secret. The value is write-only: it is never returned.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationClientSecret", title = "Client secret",
        description = "A client secret of an application, managed by the Automation API. A secret is immutable: to " +
                "rotate it, create a secret with a new key, then delete the old one.")
public class AutomationClientSecret implements DryRunResult {

    @NotNull
    @Size(min = 1, max = ApplicationSecretService.KEY_MAX_LENGTH)
    @JsonProperty("key")
    @Schema(name = "key", title = "Key",
            description = "Stable, immutable identifier for the secret within its application, also used as the " +
                    "read-only name of the secret. Lowercase alphanumeric and hyphens, starting and ending with an " +
                    "alphanumeric character. At most 64 characters. A secret created outside the Automation API is " +
                    "addressed as id:<uuid>.",
            example = "ci-2026-10")
    private String automationKey;

    @NotNull
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY,
            description = "Value of the secret. Write-only: never returned. Required on every request: sending the " +
                    "stored value again is a no-op, a different value is rejected with 400. At least 43 characters; " +
                    "generate it from a cryptographically secure random source with at least 32 bytes of entropy " +
                    "(for example 32 random bytes, base64url-encoded), and never reuse it across applications. With " +
                    "BCrypt hashing, at most 72 bytes in UTF-8 (BCrypt ignores the rest); with no hashing, at most " +
                    "374 characters. An application using client_secret_jwt stores the secret without hashing, " +
                    "since the gateway needs the value to verify the JWT signature. " +
                    "Switching an application that holds hashed secrets to client_secret_jwt is rejected with 400: " +
                    "delete its secrets, apply the application, then apply the secrets again.",
            example = "q8Zk3vN2xT7pR1mW9yB4cD6fH0jL5sA8eG2iK7oU3wQ")
    private String value;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Schema(accessMode = Schema.AccessMode.READ_ONLY,
            description = "Expiration timestamp (ISO-8601 / RFC 3339, UTC), set from the secret expiration settings " +
                    "of the application, else of the domain. Absent when the secret does not expire. Read-only.")
    private Date expiresAt;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(accessMode = Schema.AccessMode.READ_ONLY,
            description = "Validation errors returned when dryRun is true. Absent when validation succeeds.")
    @Setter(lombok.AccessLevel.NONE)
    private List<DryRunError> dryRunErrors;

    public void setDryRunErrors(List<DryRunError> dryRunErrors) {
        this.dryRunErrors = dryRunErrors != null && !dryRunErrors.isEmpty() ? dryRunErrors : null;
    }
}
