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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Domain certificate settings as exposed by the Automation API.
 * <p>
 * {@code fallbackCertificate} references — by {@code key} — one of the certificates
 * declared in the same Domain request.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationCertificateSettings", title = "Certificate settings",
        description = "Domain-level certificate settings.")
public class AutomationCertificateSettings {

    @Schema(description = "Key of a certificate managed under this domain, used as the fallback certificate " +
            "when a client does not specify one. Must reference a certificate created via the domain's " +
            "certificate endpoints.",
            example = "default")
    private String fallbackCertificate;
}
