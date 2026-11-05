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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
