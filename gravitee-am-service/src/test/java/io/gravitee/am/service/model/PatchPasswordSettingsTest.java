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
package io.gravitee.am.service.model;

import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.service.exception.InvalidParameterException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class PatchPasswordSettingsTest {
    @Test(expected = InvalidParameterException.class)
    public void shouldRejectNegativeLength() {
        final var settings = new PatchPasswordSettings();
        settings.setMinLength(Optional.of(-1));

        settings.patch(new PasswordSettings());
    }

    @Test(expected = InvalidParameterException.class)
    public void shouldRejectZeroMinLength() {
        final var settings = new PatchPasswordSettings();
        settings.setMinLength(Optional.of(0));

        settings.patch(new PasswordSettings());
    }

    @Test(expected = InvalidParameterException.class)
    public void shouldRejectZeroMaxLength() {
        final var settings = new PatchPasswordSettings();
        settings.setMaxLength(Optional.of(0));

        settings.patch(new PasswordSettings());
    }

    @Test(expected = InvalidParameterException.class)
    public void shouldRejectMaxMinMismatch() {
        final var settings = new PatchPasswordSettings();
        settings.setMaxLength(Optional.of(8));
        settings.setMinLength(Optional.of(10));

        settings.patch(new PasswordSettings());
    }

    @Test
    public void shouldUpdate() {
        final var settings = new PatchPasswordSettings();
        settings.setMinLength(Optional.of(8));
        settings.setMaxLength(Optional.of(10));
        final var updatedSettings = settings.patch(new PasswordSettings());

        Assert.assertEquals(Integer.valueOf(8), updatedSettings.getMinLength());
        Assert.assertEquals(Integer.valueOf(10), updatedSettings.getMaxLength());
    }

    @Test
    public void shouldUpdatePasswordPolicyInherit() {
        final var settings = new PatchPasswordSettings();
        settings.setInherited(Optional.of(false));

        final var updatedSettings = settings.patch(null);

        assertEquals(Integer.valueOf(8), updatedSettings.getMinLength());
        assertEquals(Integer.valueOf(128), updatedSettings.getMaxLength());
        assertFalse(updatedSettings.isInherited());
        assertFalse(updatedSettings.isPasswordHistoryEnabled());
    }

    @Test
    public void shouldEnablePasswordHistory() {
        final var settings = new PatchPasswordSettings();
        settings.setInherited(Optional.of(false));
        settings.setPasswordHistoryEnabled(Optional.of(true));
        settings.setOldPasswords(Optional.of(Short.valueOf("2")));

        final var updatedSettings = settings.patch(null);

        assertTrue(updatedSettings.isPasswordHistoryEnabled());
    }
}
