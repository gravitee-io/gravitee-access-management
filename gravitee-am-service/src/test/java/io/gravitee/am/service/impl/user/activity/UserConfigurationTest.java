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

package io.gravitee.am.service.impl.user.activity;

import io.gravitee.am.service.impl.user.activity.configuration.UserActivityConfiguration;
import io.gravitee.am.service.impl.user.activity.configuration.UserActivityConfiguration.Algorithm;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConfigurationTest {


    @Test
    public void must_load_correctly_configuration_with_null() {
        new UserActivityConfiguration(true,
                Algorithm.NONE,
                null, 3600,
                ChronoUnit.SECONDS,
                0.07,
                0.07);
    }

    @Test
    public void must_load_correctly_configuration_with_valid_key() {
        new UserActivityConfiguration(true,
                Algorithm.NONE,
                "a16validsaltkey", 3600,
                ChronoUnit.SECONDS,
                0.07,
                0.07);
    }

    @Test
    public void must_throw_exception_due_too_to_long_key() {
        assertThrows(IllegalArgumentException.class, () -> new UserActivityConfiguration(true,
                Algorithm.NONE,
                "a16unvalidsaltedkey", 3600,
                ChronoUnit.SECONDS,
                0.07,
                0.07)
        );
    }

    @Test
    public void must_throw_exception_due_to_not_well_formatted_key() {
        assertThrows(IllegalArgumentException.class, () -> new UserActivityConfiguration(true,
                Algorithm.NONE,
                "wrong-key", 3600,
                ChronoUnit.SECONDS,
                0.07,
                0.07)
        );
    }

    private void assertThrows(Class<? extends RuntimeException> clazz, Supplier<UserActivityConfiguration> supplier) {
        try {
            supplier.get();
            // Meant to fail
            assertFalse(true);
        } catch (RuntimeException e) {
            assertEquals(clazz, e.getClass());
        }
    }
}
