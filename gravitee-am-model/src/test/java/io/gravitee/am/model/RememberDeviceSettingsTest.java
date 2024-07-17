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
package io.gravitee.am.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class RememberDeviceSettingsTest {

    private final static Random rng = new Random(1337);

    static RememberDeviceSettings[] copyCases() {
        return new RememberDeviceSettings[] {
                settings(false, false),
                settings(false, true),
                settings(true, false),
                settings(true, true)
        };
    }
    @ParameterizedTest
    @MethodSource("copyCases")
    public void copyConstructorCopiesProperly(RememberDeviceSettings source) {
        assertThat(new RememberDeviceSettings(source))
                .usingRecursiveComparison()
                .isEqualTo(source);
    }

    private static RememberDeviceSettings settings(boolean active, boolean skipRememberDevice) {
        var settings = new RememberDeviceSettings();
        settings.setActive(active);
        settings.setSkipRememberDevice(skipRememberDevice);
        settings.setExpirationTimeSeconds(rng.nextLong(0,100000));
        settings.setDeviceIdentifierId(new UUID(rng.nextLong(), rng.nextLong()).toString());
        return settings;
    }

}
