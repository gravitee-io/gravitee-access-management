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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class PatchPasswordSettingsTest {
    @Test
    public void shouldRejectNegativeLength() {
        assertThrows(InvalidParameterException.class, () -> {
            final var settings = new PatchPasswordSettings();
            settings.setMinLength(Optional.of(-1));

            settings.patch(new PasswordSettings());
        });
    }

    @Test
    public void shouldRejectZeroMinLength() {
        assertThrows(InvalidParameterException.class, () -> {
            final var settings = new PatchPasswordSettings();
            settings.setMinLength(Optional.of(0));

            settings.patch(new PasswordSettings());
        });
    }

    @Test
    public void shouldRejectZeroMaxLength() {

        assertThrows(InvalidParameterException.class, () -> {
            final var settings = new PatchPasswordSettings();
            settings.setMaxLength(Optional.of(0));

            settings.patch(new PasswordSettings());
        });

    }

    @Test
    public void shouldRejectMaxMinMismatch() {
        assertThrows(InvalidParameterException.class, () -> {
            final var settings = new PatchPasswordSettings();
            settings.setMaxLength(Optional.of(8));
            settings.setMinLength(Optional.of(10));

            settings.patch(new PasswordSettings());
        });
    }

    @Test
    public void shouldUpdate() {
        final var settings = new PatchPasswordSettings();
        settings.setMinLength(Optional.of(8));
        settings.setMaxLength(Optional.of(10));
        final var updatedSettings = settings.patch(new PasswordSettings());

        assertEquals(Integer.valueOf(8), updatedSettings.getMinLength());
        assertEquals(Integer.valueOf(10), updatedSettings.getMaxLength());
    }
}
