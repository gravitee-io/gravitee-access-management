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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConsentRelayStrategyRegistryTest {

    /** Public, no-arg: discoverable via META-INF/spring.factories on the test classpath. */
    public static final class UpperTypeStrategy implements ConsentRelayStrategy {
        public String id() { return "test-upper"; }
        public List<Map<String, Object>> relay(List<Map<String, Object>> ad, ConsentRelayContext ctx) {
            return ad; // identity is fine for discovery/resolution tests
        }
    }

    @Test
    void discovers_strategies_from_the_classpath_and_resolves_by_id() {
        ConsentRelayStrategyRegistry reg =
                ConsentRelayStrategyRegistry.discover(getClass().getClassLoader());
        assertTrue(reg.resolve("test-upper") instanceof UpperTypeStrategy);
    }

    @Test
    void unknown_id_fails_fast_listing_installed_ids() {
        ConsentRelayStrategyRegistry reg =
                ConsentRelayStrategyRegistry.discover(getClass().getClassLoader());
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> reg.resolve("nope"));
        assertTrue(ex.getMessage().contains("test-upper"));
    }

    @Test
    void duplicate_ids_fail_fast() {
        ConsentRelayStrategy a = new UpperTypeStrategy();
        ConsentRelayStrategy b = new UpperTypeStrategy();
        assertThrows(IllegalStateException.class,
                () -> new ConsentRelayStrategyRegistry(List.of(a, b)));
    }
}
