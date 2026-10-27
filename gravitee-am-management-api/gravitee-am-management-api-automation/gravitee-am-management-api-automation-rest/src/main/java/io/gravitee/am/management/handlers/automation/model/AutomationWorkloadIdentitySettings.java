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

import io.gravitee.am.model.application.SpiffeApplicationSettings.SubjectMatchMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Automation API mirror of {@link io.gravitee.am.model.application.SpiffeApplicationSettings}.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationWorkloadIdentitySettings", title = "Workload identity settings",
        description = "SPIFFE workload identity the application authenticates with (JWT-SVID client authentication).")
public class AutomationWorkloadIdentitySettings {

    @Schema(description = "Name of the trusted domain this application authenticates against.",
            example = "prod.example.org")
    private String trustDomain;

    @Schema(description = "SPIFFE ID expected in the SVID sub claim, interpreted per subjectMatchMode.",
            example = "spiffe://prod.example.org/ns/payments/sa/api")
    private String subject;

    @Schema(description = "How subject is matched against the SVID sub claim. exact (default) requires equality. " +
            "prefix is only allowed for hosted_delegated or autonomous agent applications: subject must end with / " +
            "and the SVID sub is accepted when it starts with subject; the full SVID SPIFFE ID then becomes the " +
            "per-instance act.sub in minted tokens.", defaultValue = "exact")
    private SubjectMatchMode subjectMatchMode = SubjectMatchMode.EXACT;
}
