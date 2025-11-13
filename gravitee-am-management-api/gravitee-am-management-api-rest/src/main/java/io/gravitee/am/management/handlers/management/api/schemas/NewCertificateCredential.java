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
package io.gravitee.am.management.handlers.management.api.schemas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "NewCertificateCredential")
public class NewCertificateCredential {

    @NotNull
    @NotBlank
    @Schema(description = "The certificate in PEM format", required = true, example = "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----")
    private String certificatePem;

    @Schema(description = "Optional device name for the certificate", example = "My Laptop")
    private String deviceName;
}

