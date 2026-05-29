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
public class AutomationCIBASettings {

    private boolean enabled;

    /** Validity (in sec) of the {@code auth_req_id}. */
    private int authReqExpiry = io.gravitee.am.model.oidc.CIBASettings.DEFAULT_EXPIRY_IN_SEC;

    /** Delay between two calls on the token endpoint using the same {@code auth_req_id}. */
    private int tokenReqInterval = io.gravitee.am.model.oidc.CIBASettings.DEFAULT_INTERVAL_IN_SEC;

    /** Max length of the {@code binding_message} parameter. */
    private int bindingMessageLength = io.gravitee.am.model.oidc.CIBASettings.DEFAULT_MSG_LENGTH;
}
