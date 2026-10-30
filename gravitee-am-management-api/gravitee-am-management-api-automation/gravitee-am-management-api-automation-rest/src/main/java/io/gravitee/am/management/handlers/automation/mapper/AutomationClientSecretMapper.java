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
package io.gravitee.am.management.handlers.automation.mapper;

import io.gravitee.am.management.handlers.automation.model.AutomationClientSecret;
import io.gravitee.am.management.handlers.automation.resource.AutomationRef;
import io.gravitee.am.model.application.ClientSecret;

/**
 * Maps a {@link ClientSecret} to its {@link AutomationClientSecret} projection. The value is never mapped.
 *
 * @author GraviteeSource Team
 */
public final class AutomationClientSecretMapper {

    private AutomationClientSecretMapper() {
    }

    public static AutomationClientSecret toAutomationClientSecret(ClientSecret secret) {
        AutomationClientSecret out = new AutomationClientSecret();
        out.setAutomationKey(secret.getAutomationKey() != null ? secret.getAutomationKey() : AutomationRef.ID_PREFIX + secret.getId());
        out.setExpiresAt(secret.getExpiresAt());
        return out;
    }
}
