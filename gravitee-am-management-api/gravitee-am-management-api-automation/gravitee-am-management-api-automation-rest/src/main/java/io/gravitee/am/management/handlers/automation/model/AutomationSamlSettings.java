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
 * Domain SAML 2.0 IdP settings as exposed by the Automation API.
 * <p>
 * {@code certificate} references — by {@code key} — one of the certificates declared
 * in the same Domain request, keeping the SAML signing certificate atomically
 * consistent with the domain's certificates.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class AutomationSamlSettings {

    private boolean enabled;

    private String entityId;

    /**
     * The {@code key} of one of the certificates declared in the same Domain request.
     */
    private String certificate;
}
