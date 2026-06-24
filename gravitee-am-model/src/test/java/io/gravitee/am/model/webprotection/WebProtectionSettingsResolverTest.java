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
import org.junit.Test;

import static io.gravitee.am.model.webprotection.WebProtectionResolution.DISABLED;
import static io.gravitee.am.model.webprotection.WebProtectionResolution.ENABLED;
import static io.gravitee.am.model.webprotection.WebProtectionResolution.INHERIT;
import static org.junit.Assert.assertEquals;

public class WebProtectionSettingsResolverTest {

    @Test
    public void shouldInheritWhenSettingsAreMissing() {
        assertEquals(INHERIT, WebProtectionSettingsResolver.resolve((CspSettings) null));
    }

    @Test
    public void shouldInheritWhenExplicitlyInherited() {
        final CspSettings settings = new CspSettings();
        settings.setInherited(true);
        settings.setEnabled(true);

        assertEquals(INHERIT, WebProtectionSettingsResolver.resolve(settings));
    }

    @Test
    public void shouldDisableWhenNotInheritedAndDisabled() {
        final CspSettings settings = new CspSettings();
        settings.setInherited(false);
        settings.setEnabled(false);

        assertEquals(DISABLED, WebProtectionSettingsResolver.resolve(settings));
    }

    @Test
    public void shouldEnableWhenNotInheritedAndEnabled() {
        final CspSettings settings = new CspSettings();
        settings.setInherited(false);
        settings.setEnabled(true);

        assertEquals(ENABLED, WebProtectionSettingsResolver.resolve(settings));
    }

    @Test
    public void shouldUseLegacyEnabledOverrideWhenInheritedIsNull() {
        final CorsSettings settings = new CorsSettings();
        settings.setEnabled(true);

        assertEquals(ENABLED, WebProtectionSettingsResolver.resolve(settings));
    }

    @Test
    public void shouldUseLegacyInheritWhenInheritedIsNullAndDisabled() {
        final CorsSettings settings = new CorsSettings();
        settings.setEnabled(false);

        assertEquals(INHERIT, WebProtectionSettingsResolver.resolve(settings));
    }
}
