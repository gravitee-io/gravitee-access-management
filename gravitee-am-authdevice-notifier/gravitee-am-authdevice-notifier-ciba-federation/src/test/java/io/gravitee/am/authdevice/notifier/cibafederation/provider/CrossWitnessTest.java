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

class CrossWitnessTest {

    @Test
    void canonicalisation_is_key_order_independent() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("b", 1); a.put("a", 2);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("a", 2); b.put("b", 1);
        assertEquals(CrossWitness.hash(a), CrossWitness.hash(b));
    }

    @Test
    void canonicalisation_preserves_array_order() {
        assertNotEquals(CrossWitness.hash(List.of(1, 2)), CrossWitness.hash(List.of(2, 1)));
    }

    @Test
    void nested_objects_sorted_recursively() {
        Map<String, Object> inner1 = new LinkedHashMap<>(); inner1.put("y", 1); inner1.put("x", 2);
        Map<String, Object> inner2 = new LinkedHashMap<>(); inner2.put("x", 2); inner2.put("y", 1);
        assertEquals(CrossWitness.hash(Map.of("k", inner1)), CrossWitness.hash(Map.of("k", inner2)));
    }

    @Test
    void matchesHash_true_for_equal_payloads_and_false_otherwise() {
        Object p = List.of(Map.of("type", "fdx_v1.0"));
        String expected = CrossWitness.hash(p);
        assertTrue(CrossWitness.matchesHash(expected, List.of(Map.of("type", "fdx_v1.0"))));
        assertFalse(CrossWitness.matchesHash(expected, List.of(Map.of("type", "other"))));
        assertFalse(CrossWitness.matchesHash(expected, null), "null value never matches");
        assertFalse(CrossWitness.matchesHash(null, p), "null expected hash never matches");
    }

    // Fix 1: whole-valued floats must hash equal to integers
    @Test
    void whole_valued_doubles_hash_equal_to_integers() {
        assertEquals(CrossWitness.hash(java.util.Map.of("display_order", 1)),
                     CrossWitness.hash(java.util.Map.of("display_order", 1.0d)));
        // non-whole doubles are distinct from their truncation
        assertNotEquals(CrossWitness.hash(java.util.Map.of("amt", 1.5d)),
                        CrossWitness.hash(java.util.Map.of("amt", 1)));
    }

    // Fix 2: control characters are escaped and produce a deterministic hash.
    // BEL (0x07) is < 0x20 but not \n/\r/\t so must become \u0007 in canonical form.
    @Test
    void control_characters_are_escaped_and_deterministic() {
        // "ab" + BEL (0x07) + "c"
        String withCtrl = "abc";
        // The canonical JSON form must contain the \u0007 escape, not the raw byte
        String canonical = CrossWitness.canonical(java.util.Map.of("k", withCtrl));
        assertTrue(canonical.contains("\\u0007"),
                   "Expected \\u0007 in canonical form, got: " + canonical);
        // Idempotent: same input hashes the same way every time
        assertEquals(CrossWitness.hash(java.util.Map.of("k", withCtrl)),
                     CrossWitness.hash(java.util.Map.of("k", withCtrl)));
    }

    @Test
    void matchesHash_true_on_match_false_on_mismatch_and_nulls() {
        Object payload = java.util.List.of(java.util.Map.of("type", "fdx_v1.0"));
        String h = CrossWitness.hash(payload);
        assertTrue(CrossWitness.matchesHash(h, java.util.List.of(java.util.Map.of("type", "fdx_v1.0"))));
        assertFalse(CrossWitness.matchesHash(h, java.util.List.of(java.util.Map.of("type", "other"))));
        assertFalse(CrossWitness.matchesHash(null, payload));
        assertFalse(CrossWitness.matchesHash(h, null));
    }

    // Fix 3: null leaf values are handled deterministically and differ from a present string
    @Test
    void null_leaf_values_are_handled() {
        java.util.Map<String, Object> withNull = new java.util.HashMap<>();
        withNull.put("scope", null);
        java.util.Map<String, Object> withNull2 = new java.util.HashMap<>();
        withNull2.put("scope", null);
        assertEquals(CrossWitness.hash(withNull), CrossWitness.hash(withNull2));
        assertNotEquals(CrossWitness.hash(withNull), CrossWitness.hash(java.util.Map.of("scope", "x")));
    }
}
