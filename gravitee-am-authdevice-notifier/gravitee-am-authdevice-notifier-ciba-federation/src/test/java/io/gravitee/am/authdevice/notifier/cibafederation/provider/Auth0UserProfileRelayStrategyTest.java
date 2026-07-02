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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The strategy is a thin wrapper that delegates to {@link Auth0UserProfileConsentRenderer}. Output-shape behaviour
 * (labels, truncation, degraded inputs, null handling) is the renderer's contract and is owned by
 * {@link Auth0UserProfileConsentRendererTest} — we do NOT re-test it here. These cases prove only what the wrapper
 * adds: that it delegates to the renderer and that the constructor-captured recipient reaches render.
 */
class Auth0UserProfileRelayStrategyTest {

    private List<Map<String, Object>> fdx(String duration, List<String> clusters) {
        Map<String, Object> resource = Map.of("dataClusters", clusters);
        Map<String, Object> consentRequest = new HashMap<>();
        consentRequest.put("durationType", duration);
        consentRequest.put("resources", List.of(resource));
        return List.of(Map.of("type", "fdx_v1.0", "consentRequest", consentRequest));
    }

    /** Delegation: the strategy returns exactly what the renderer produces for the same (recipient, fdx). */
    @Test
    void relay_returns_exactly_what_the_renderer_produces() {
        List<Map<String, Object>> fdx = fdx("ONE_TIME", List.of("ACCOUNT_BASIC", "TRANSACTIONS"));
        Object viaStrategy = new Auth0UserProfileRelayStrategy("Acme Ltd").relay(fdx);
        List<Map<String, Object>> viaRenderer = new Auth0UserProfileConsentRenderer().render(fdx, "Acme Ltd");
        assertEquals(viaRenderer, viaStrategy);
    }

    /** Delegation also holds for a null recipient (renderer's null handling is its own contract). */
    @Test
    void relay_delegates_to_renderer_for_null_recipient() {
        List<Map<String, Object>> fdx = fdx("PERSISTENT", List.of("STATEMENTS"));
        Object viaStrategy = new Auth0UserProfileRelayStrategy(null).relay(fdx);
        List<Map<String, Object>> viaRenderer = new Auth0UserProfileConsentRenderer().render(fdx, null);
        assertEquals(viaRenderer, viaStrategy);
    }

    /**
     * Constructor wiring: the recipient captured by the constructor actually reaches render. The renderer
     * surfaces the recipient as {@code properties.shared_with.value}, so two strategies built with different
     * recipients must produce correspondingly different output for the same fdx input.
     */
    @SuppressWarnings("unchecked")
    @Test
    void constructor_recipient_reaches_render() {
        List<Map<String, Object>> fdx = fdx("ONE_TIME", List.of("ACCOUNT_BASIC"));

        Map<String, Object> outAcme = ((List<Map<String, Object>>) new Auth0UserProfileRelayStrategy("Acme Ltd").relay(fdx)).get(0);
        Map<String, Object> outBeta = ((List<Map<String, Object>>) new Auth0UserProfileRelayStrategy("Beta Corp").relay(fdx)).get(0);

        assertEquals("Acme Ltd", ((Map<String, Object>) ((Map<String, Object>) outAcme.get("properties")).get("shared_with")).get("value"));
        assertEquals("Beta Corp", ((Map<String, Object>) ((Map<String, Object>) outBeta.get("properties")).get("shared_with")).get("value"));
        assertNotEquals(outAcme, outBeta);
    }
}
