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

class Auth0UserProfileConsentRendererTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> propsOf(List<Map<String, Object>> out) {
        assertEquals(1, out.size());
        assertEquals("urn:auth0:schemas:authorization-details:v1:user-profile", out.get(0).get("type"));
        return (Map<String, Object>) out.get(0).get("properties");
    }

    @SuppressWarnings("unchecked")
    private String value(Map<String, Object> props, String key) {
        return (String) ((Map<String, Object>) props.get(key)).get("value");
    }

    /** An FDX-shaped payload is used here purely as an example input; the renderer must not depend on it. */
    private List<Map<String, Object>> fdxExample(String duration, List<String> clusters) {
        Map<String, Object> resource = Map.of("dataClusters", clusters);
        Map<String, Object> consentRequest = new HashMap<>();
        consentRequest.put("durationType", duration);
        consentRequest.put("resources", List.of(resource));
        return List.of(Map.of("type", "fdx_v1.0", "consentRequest", consentRequest));
    }

    @Test
    void renders_user_profile_urn_surfacing_type_and_scalar_leaves() {
        var props = propsOf(new Auth0UserProfileConsentRenderer()
                .render(fdxExample("ONE_TIME", List.of("ACCOUNT_BASIC", "TRANSACTIONS")), "Acme Ltd"));
        assertEquals("Acme Ltd", value(props, "shared_with"));
        assertEquals("fdx_v1.0", value(props, "requested"));
        // Schema-agnostic: scalar leaf values surface without any FDX-specific knowledge.
        String details = value(props, "details");
        assertTrue(details.contains("ONE_TIME"), details);
        assertTrue(details.contains("ACCOUNT_BASIC"), details);
        assertTrue(details.contains("TRANSACTIONS"), details);
    }

    @Test
    void renders_arbitrary_non_fdx_authorization_details() {
        // No FDX vocabulary at all — proves the renderer is schema-agnostic.
        List<Map<String, Object>> ad = List.of(new LinkedHashMap<>(Map.of(
                "type", "payment_initiation", "amount", "100.00", "currency", "GBP")));
        var props = propsOf(new Auth0UserProfileConsentRenderer().render(ad, "PSP"));
        assertEquals("payment_initiation", value(props, "requested"));
        String details = value(props, "details");
        assertTrue(details.contains("100.00"), details);
        assertTrue(details.contains("GBP"), details);
    }

    @Test
    void multiple_types_are_joined() {
        List<Map<String, Object>> ad = List.of(Map.of("type", "a"), Map.of("type", "b"));
        var props = propsOf(new Auth0UserProfileConsentRenderer().render(ad, "R"));
        assertEquals("a, b", value(props, "requested"));
    }

    @Test
    void values_truncated_to_255_chars() {
        var props = propsOf(new Auth0UserProfileConsentRenderer()
                .render(fdxExample("ONE_TIME", List.of("ACCOUNT_BASIC")), "R".repeat(300)));
        assertEquals(255, value(props, "shared_with").length());
    }

    @Test
    void null_input_list_renders_without_throwing() {
        var props = propsOf(new Auth0UserProfileConsentRenderer().render(null, "Acme"));
        assertEquals("Acme", value(props, "shared_with"));
        assertEquals("account data", value(props, "requested"));
        assertNull(props.get("details"), "no details property when nothing was requested");
    }

    @Test
    void empty_input_list_renders_without_throwing() {
        var out = new Auth0UserProfileConsentRenderer().render(List.of(), "Acme");
        assertEquals(1, out.size());
    }

    @Test
    void null_recipient_does_not_leak_into_output() {
        var out = new Auth0UserProfileConsentRenderer().render(List.of(Map.of("type", "x")), null);
        var props = propsOf(out);
        assertEquals("", value(props, "shared_with"));
        assertFalse(String.valueOf(out.get(0).get("instruction")).startsWith("null"));
    }
}
