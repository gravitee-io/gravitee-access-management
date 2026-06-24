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
package io.gravitee.am.model.webprotection;

import io.gravitee.am.model.CorsSettings;

/**
 * Resolves how web protection settings should be applied for a security domain.
 * <p>
 * When {@code inherited} is {@code null} (legacy records), {@code enabled = true} means override and
 * {@code enabled = false} means inherit from gravitee.yml.
 */
public final class WebProtectionSettingsResolver {

    private WebProtectionSettingsResolver() {
    }

    public static WebProtectionResolution resolve(CorsSettings settings) {
        return resolve(settings == null ? null : settings.getInherited(), settings != null && settings.isEnabled());
    }

    public static WebProtectionResolution resolve(CspSettings settings) {
        return resolve(settings == null ? null : settings.getInherited(), settings != null && settings.isEnabled());
    }

    public static WebProtectionResolution resolve(XFrameSettings settings) {
        return resolve(settings == null ? null : settings.getInherited(), settings != null && settings.isEnabled());
    }

    public static WebProtectionResolution resolve(XssProtectionSettings settings) {
        return resolve(settings == null ? null : settings.getInherited(), settings != null && settings.isEnabled());
    }

    private static WebProtectionResolution resolve(Boolean inherited, boolean enabled) {
        if (inherited == null) {
            return enabled ? WebProtectionResolution.ENABLED : WebProtectionResolution.INHERIT;
        }
        if (inherited) {
            return WebProtectionResolution.INHERIT;
        }
        return enabled ? WebProtectionResolution.ENABLED : WebProtectionResolution.DISABLED;
    }
}
