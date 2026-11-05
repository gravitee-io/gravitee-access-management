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
package io.gravitee.am.model.oauth2;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationDetailTest {

    @Test
    void shouldReadTheCommonMembers() {
        AuthorizationDetail detail = new AuthorizationDetail(Map.of(
                "type", "account_information",
                "locations", List.of("https://example.com/accounts"),
                "actions", List.of("list_accounts", "read_balances"),
                "datatypes", List.of("balances"),
                "identifier", "account-1",
                "privileges", List.of("admin")));

        assertEquals("account_information", detail.getType());
        assertEquals(List.of("https://example.com/accounts"), detail.getLocations());
        assertEquals(List.of("list_accounts", "read_balances"), detail.getActions());
        assertEquals(List.of("balances"), detail.getDatatypes());
        assertEquals("account-1", detail.getIdentifier());
        assertEquals(List.of("admin"), detail.getPrivileges());
    }

    @Test
    void shouldReadAbsentCommonMembersAsEmpty() {
        AuthorizationDetail detail = new AuthorizationDetail(Map.of("type", "account_information"));

        assertNull(detail.getIdentifier());
        assertTrue(detail.getLocations().isEmpty());
        assertTrue(detail.getActions().isEmpty());
        assertTrue(detail.getDatatypes().isEmpty());
        assertTrue(detail.getPrivileges().isEmpty());
    }

    @Test
    void shouldRejectChanges() {
        AuthorizationDetail detail = new AuthorizationDetail(Map.of(
                "type", "payment_initiation",
                "instructedAmount", Map.of("currency", "EUR"),
                "actions", List.of("initiate")));
        Map<?, ?> instructedAmount = (Map<?, ?>) detail.get("instructedAmount");
        List<?> actions = (List<?>) detail.get("actions");

        assertThrows(UnsupportedOperationException.class, () -> detail.put("type", "account_information"));
        assertThrows(UnsupportedOperationException.class, () -> detail.remove("type"));
        assertThrows(UnsupportedOperationException.class, () -> detail.entrySet().iterator().next().setValue("account_information"));
        assertThrows(UnsupportedOperationException.class, () -> instructedAmount.clear());
        assertThrows(UnsupportedOperationException.class, () -> actions.clear());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIgnoreLaterChangesToItsSource() {
        Map<String, Object> instructedAmount = new HashMap<>(Map.of("currency", "EUR"));
        List<Object> actions = new ArrayList<>(List.of(new HashMap<>(Map.of("name", "initiate"))));
        Map<String, Object> source = new HashMap<>(Map.of("type", "payment_initiation", "instructedAmount", instructedAmount, "actions", actions));

        AuthorizationDetail detail = new AuthorizationDetail(source);
        source.put("type", "account_information");
        instructedAmount.put("currency", "USD");
        actions.add("refund");
        ((Map<String, Object>) actions.get(0)).put("name", "cancel");

        assertEquals(Map.of(
                "type", "payment_initiation",
                "instructedAmount", Map.of("currency", "EUR"),
                "actions", List.of(Map.of("name", "initiate"))), detail);
    }

    @Test
    void shouldKeepMemberOrderAndNullValues() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "payment_initiation");
        source.put("zeta", null);
        source.put("alpha", Arrays.asList(null, Map.of("b", 1)));

        AuthorizationDetail detail = new AuthorizationDetail(source);

        assertEquals(List.of("type", "zeta", "alpha"), List.copyOf(detail.keySet()));
        assertTrue(detail.containsKey("zeta"));
        assertNull(detail.get("zeta"));
        assertEquals(Arrays.asList(null, Map.of("b", 1)), detail.get("alpha"));
    }

    @Test
    void shouldIgnoreCommonMembersOfAnotherJsonType() {
        AuthorizationDetail detail = new AuthorizationDetail(Map.of(
                "type", 42,
                "identifier", List.of("account-1"),
                "actions", "read",
                "locations", Arrays.asList("https://example.com", 1, null)));

        assertNull(detail.getType());
        assertNull(detail.getIdentifier());
        assertTrue(detail.getActions().isEmpty());
        assertEquals(List.of("https://example.com"), detail.getLocations());
        assertEquals(42, detail.get("type"));
    }
}
