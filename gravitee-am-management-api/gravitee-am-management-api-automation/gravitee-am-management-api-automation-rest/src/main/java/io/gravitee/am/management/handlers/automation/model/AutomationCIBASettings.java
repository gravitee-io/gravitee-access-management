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
 * Automation API mirror of {@link io.gravitee.am.model.oidc.CIBASettings}. Device notifiers
 * are out of scope for the Automation API and are intentionally not surfaced.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(name = "AutomationCIBASettings", title = "CIBA settings",
        description = "Client-Initiated Backchannel Authentication (CIBA) settings for the domain. " +
                "CIBA lets a relying party initiate end-user authentication from a separate consumption " +
                "device, without redirecting the user through the browser. Authentication device notifiers " +
                "are not managed by the Automation API and are not exposed here.")
public class AutomationCIBASettings {

    @Schema(description = "Whether Client-Initiated Backchannel Authentication is enabled for the domain.",
            defaultValue = "false")
    private boolean enabled;

    @Schema(description = "Default validity period, in seconds, of the issued auth_req_id.", example = "600")
    private int authReqExpiry = io.gravitee.am.model.oidc.CIBASettings.DEFAULT_EXPIRY_IN_SEC;

    @Schema(description = "Minimum delay, in seconds, that a client must wait between two polls of the token " +
            "endpoint for the same auth_req_id (POLL or PING delivery mode).", example = "5")
    private int tokenReqInterval = io.gravitee.am.model.oidc.CIBASettings.DEFAULT_INTERVAL_IN_SEC;

    @Schema(description = "Maximum number of characters accepted for the binding_message parameter.", example = "256")
    private int bindingMessageLength = io.gravitee.am.model.oidc.CIBASettings.DEFAULT_MSG_LENGTH;
}
