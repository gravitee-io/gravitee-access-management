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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationDetailsTest {

    private static final Map<String, Object> PAYMENT_EUR = Map.of("type", "payment_initiation", "currency", "EUR");
    private static final Map<String, Object> ACCOUNT = Map.of("type", "account_information");
    private static final Map<String, Object> PAYMENT_USD = Map.of("type", "payment_initiation", "currency", "USD");

    @Test
    void shouldKeepTheElementsInRequestOrder() {
        AuthorizationDetails details = AuthorizationDetails.of(List.of(PAYMENT_EUR, ACCOUNT, PAYMENT_USD));

        assertEquals(List.of(PAYMENT_EUR, ACCOUNT, PAYMENT_USD), details.getRaw());
        assertEquals(List.of("payment_initiation", "account_information", "payment_initiation"),
                details.getAll().stream().map(AuthorizationDetail::getType).toList());
    }

    @Test
    void shouldGroupTheElementsByTypeInOrderOfFirstAppearance() {
        AuthorizationDetails details = AuthorizationDetails.of(List.of(PAYMENT_EUR, ACCOUNT, PAYMENT_USD));

        assertEquals(List.of("payment_initiation", "account_information"), List.copyOf(details.getByType().keySet()));
        assertEquals(List.of(PAYMENT_EUR, PAYMENT_USD), details.getByType().get("payment_initiation"));
        assertEquals(List.of(ACCOUNT), details.getByType().get("account_information"));
        assertEquals(Set.of("payment_initiation", "account_information"), details.getTypes());
    }

    @Test
    void shouldLeaveElementsWithoutStringTypeOutOfTheGroups() {
        Map<String, Object> untyped = Map.of("type", 42);

        AuthorizationDetails details = AuthorizationDetails.of(List.of(ACCOUNT, untyped));

        assertEquals(List.of(ACCOUNT, untyped), details.getRaw());
        assertEquals(Set.of("account_information"), details.getTypes());
    }

    @Test
    void shouldReuseElementsThatAreAlreadyTyped() {
        AuthorizationDetail account = new AuthorizationDetail(ACCOUNT);

        assertSame(account, AuthorizationDetails.of(List.of(account)).getAll().get(0));
    }

    @Test
    void shouldBeReadOnly() {
        AuthorizationDetails details = AuthorizationDetails.of(List.of(ACCOUNT));

        assertThrows(UnsupportedOperationException.class, () -> details.getAll().add(new AuthorizationDetail()));
        assertThrows(UnsupportedOperationException.class, () -> details.getRaw().clear());
        assertThrows(UnsupportedOperationException.class, () -> details.getByType().clear());
        assertThrows(UnsupportedOperationException.class, () -> details.getByType().get("account_information").clear());
    }

    @Test
    void shouldTellWhetherItIsEmpty() {
        assertTrue(AuthorizationDetails.of(List.of()).isEmpty());
        assertFalse(AuthorizationDetails.of(List.of(ACCOUNT)).isEmpty());
    }

    @Test
    void shouldSerializeAsTheArrayItWasReadFrom() throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        String json = "[{\"type\":\"payment_initiation\",\"amount\":10.50,\"nested\":{\"b\":1,\"a\":[true]}},{\"type\":\"account_information\"}]";

        AuthorizationDetails details = mapper.readValue(json, AuthorizationDetails.class);

        assertEquals(new BigDecimal("10.50"), details.getAll().get(0).get("amount"));
        assertEquals(json, mapper.writeValueAsString(details));
    }

    @Test
    void shouldReadElementsAsTypedDetails() throws Exception {
        List<AuthorizationDetail> elements = new ObjectMapper().readValue("[{\"type\":\"account_information\",\"actions\":[\"read\"]}]",
                new TypeReference<>() {});

        assertEquals(List.of("read"), elements.get(0).getActions());
    }
}
