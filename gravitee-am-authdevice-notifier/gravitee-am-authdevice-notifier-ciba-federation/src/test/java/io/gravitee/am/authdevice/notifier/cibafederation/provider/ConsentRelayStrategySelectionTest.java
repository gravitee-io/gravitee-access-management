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

/**
 * Behavioral coverage for {@link CibaFederationAuthenticationDeviceNotifierProvider#selectStrategy}
 * — the config-to-strategy selection glue extracted out of {@code ensureWired()}. Every other
 * provider test constructs via the {@code forTest(...)} seam (which sets {@code wired = true} and
 * bypasses {@code ensureWired()} entirely), so this glue previously had zero direct coverage:
 * blank/null id → raw relay, non-blank unknown id → fail-fast, non-blank id with no registry →
 * fail-fast, non-blank known id → resolved strategy instance.
 */
class ConsentRelayStrategySelectionTest {

    /** Public, no-arg identity double with a fixed, known id — mirrors the module's established
     *  {@code TestStrategy} pattern (see {@link CibaFederationProviderNotifyTest}). */
    static final class StubStrategy implements ConsentRelayStrategy {
        public String id() { return "stub-strategy"; }
        public List<Map<String, Object>> relay(List<Map<String, Object>> ad, ConsentRelayContext ctx) {
            return ad; // identity; only the selection outcome (which instance) is under test here
        }
    }

    @Test
    void blank_stratId_selects_null_raw_relay() {
        ConsentRelayStrategyRegistry registry = new ConsentRelayStrategyRegistry(List.of(new StubStrategy()));
        assertNull(CibaFederationAuthenticationDeviceNotifierProvider.selectStrategy("", registry));
        assertNull(CibaFederationAuthenticationDeviceNotifierProvider.selectStrategy("   ", registry));
    }

    @Test
    void null_stratId_selects_null_raw_relay() {
        ConsentRelayStrategyRegistry registry = new ConsentRelayStrategyRegistry(List.of(new StubStrategy()));
        assertNull(CibaFederationAuthenticationDeviceNotifierProvider.selectStrategy(null, registry));
    }

    @Test
    void unknown_id_with_wired_registry_fails_fast_listing_installed_ids() {
        ConsentRelayStrategyRegistry registry = new ConsentRelayStrategyRegistry(List.of(new StubStrategy()));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CibaFederationAuthenticationDeviceNotifierProvider.selectStrategy("nope", registry));
        assertTrue(ex.getMessage().contains("stub-strategy"),
                "unknown-id failure must list the installed ids");
    }

    @Test
    void non_blank_id_with_null_registry_fails_fast() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> CibaFederationAuthenticationDeviceNotifierProvider.selectStrategy("stub-strategy", null));
        assertEquals("consentRelayStrategyRegistry not wired", ex.getMessage());
    }

    @Test
    void known_id_resolves_the_registered_strategy_instance() {
        StubStrategy strategy = new StubStrategy();
        ConsentRelayStrategyRegistry registry = new ConsentRelayStrategyRegistry(List.of(strategy));
        ConsentRelayStrategy resolved =
                CibaFederationAuthenticationDeviceNotifierProvider.selectStrategy("stub-strategy", registry);
        assertSame(strategy, resolved, "known id must resolve to the exact registered strategy instance");
    }
}
