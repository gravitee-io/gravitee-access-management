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
package io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails;

import io.gravitee.am.model.oauth2.AuthorizationDetail;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationDetailDigestTest {

    @Test
    void shouldIgnoreMemberOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("type", "payment_initiation");
        first.put("instructedAmount", Map.of("currency", "EUR", "amount", "10.00"));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("instructedAmount", Map.of("amount", "10.00", "currency", "EUR"));
        second.put("type", "payment_initiation");

        assertThat(AuthorizationDetailDigest.of(first)).isEqualTo(AuthorizationDetailDigest.of(second));
    }

    @Test
    void shouldKeepNestedArrayOrder() {
        Map<String, Object> readWrite = Map.of("type", "account_information", "actions", List.of("read", "write"));
        Map<String, Object> writeRead = Map.of("type", "account_information", "actions", List.of("write", "read"));

        assertThat(AuthorizationDetailDigest.of(readWrite)).isNotEqualTo(AuthorizationDetailDigest.of(writeRead));
    }

    @Test
    void shouldDistinguishValues() {
        assertThat(AuthorizationDetailDigest.of(Map.of("type", "a", "amount", "10")))
                .isNotEqualTo(AuthorizationDetailDigest.of(Map.of("type", "a", "amount", "11")));
    }

    @Test
    void shouldIgnoreTheOrderOfTheDetails() {
        AuthorizationDetail payment = new AuthorizationDetail(Map.of("type", "payment_initiation"));
        AuthorizationDetail account = new AuthorizationDetail(Map.of("type", "account_information"));

        assertThat(AuthorizationDetailDigest.ofAll(List.of(payment, account)))
                .isEqualTo(AuthorizationDetailDigest.ofAll(List.of(account, payment)))
                .isNotEqualTo(AuthorizationDetailDigest.ofAll(List.of(payment)));
    }
}
