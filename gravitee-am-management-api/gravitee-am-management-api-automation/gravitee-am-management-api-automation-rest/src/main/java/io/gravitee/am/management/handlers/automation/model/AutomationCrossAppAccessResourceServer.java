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

import io.gravitee.am.model.application.ApplicationCrossAppAccessResourceServer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Automation API mirror of {@link ApplicationCrossAppAccessResourceServer}: the trusted domain and the resource
 * server have no Automation API endpoint, so they are referenced by {@code id:<uuid>} only.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationCrossAppAccessResourceServer", title = "Cross App Access resource server mapping",
        description = "A resource server this application may request an ID-JAG for.")
public class AutomationCrossAppAccessResourceServer {

    @Schema(description = "Trusted domain the resource server belongs to, as id:<uuid> (a key is rejected with 400). " +
            "A hint used to resolve the resource server; the resource server reference remains authoritative.",
            example = "id:5e8f1c0a-2b3d-4f5e-8a9b-0c1d2e3f4a5b")
    private String trustDomainId;

    @Schema(description = "Resource server of the trusted domain, as id:<uuid> (a key is rejected with 400).",
            example = "id:7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
    private String resourceServerId;

    @Schema(description = "Identifier this application is registered with at the resource authorization server.",
            example = "acme-calendar-client", maxLength = ApplicationCrossAppAccessResourceServer.CLIENT_ID_MAX_LENGTH)
    private String clientId;
}
