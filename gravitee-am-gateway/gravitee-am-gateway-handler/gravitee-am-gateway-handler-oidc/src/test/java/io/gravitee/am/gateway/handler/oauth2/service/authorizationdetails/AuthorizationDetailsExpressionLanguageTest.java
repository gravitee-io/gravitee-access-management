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

import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.el.TemplateEngine;
import io.gravitee.el.spel.context.SecuredResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The expressions policies and templates can use on authorization details, under the EL whitelist.
 */
class AuthorizationDetailsExpressionLanguageTest {

    private final TemplateEngine templateEngine = TemplateEngine.templateEngine();

    @BeforeAll
    static void loadTheBuiltInWhitelist() {
        SecuredResolver.initialize(null);
    }

    @BeforeEach
    void setUp() {
        templateEngine.getTemplateContext().setVariable("authorizationDetails", AuthorizationDetails.of(List.of(
                Map.of("type", "payment_initiation", "instructedAmount", Map.of("currency", "EUR", "amount", "10.00")),
                Map.of("type", "account_information", "actions", List.of("list_accounts", "read_balances")),
                Map.of("type", "payment_initiation", "instructedAmount", Map.of("currency", "USD", "amount", "20.00")))));
    }

    @Test
    void shouldReadElementsByType() {
        assertThat(eval("{#authorizationDetails.byType['payment_initiation'][1].instructedAmount.currency}")).isEqualTo("USD");
        assertThat(eval("{#authorizationDetails.byType['payment_initiation'].size()}")).isEqualTo(2);
    }

    @Test
    void shouldTellWhetherATypeIsRequested() {
        assertThat(eval("{#authorizationDetails.byType.containsKey('account_information')}")).isEqualTo(true);
        assertThat(eval("{#authorizationDetails.types.contains('internal_transfer')}")).isEqualTo(false);
    }

    @Test
    void shouldReadCommonMembersAndTypeSpecificMembers() {
        assertThat(eval("{#authorizationDetails.all[1].actions.contains('read_balances')}")).isEqualTo(true);
        assertThat(eval("{#authorizationDetails.all[0].type}")).isEqualTo("payment_initiation");
        assertThat(eval("{#authorizationDetails.all[0]['instructedAmount']['amount']}")).isEqualTo("10.00");
    }

    @Test
    void shouldReadAbsentMembersAsNull() {
        // elements are maps, so the map accessor answers before the getters
        assertThat(eval("{#authorizationDetails.all[0].actions == null}")).isEqualTo(true);
    }

    @Test
    void shouldSelectElements() {
        assertThat(eval("{#authorizationDetails.all.?[instructedAmount != null && instructedAmount.currency == 'EUR'].size()}")).isEqualTo(1);
    }

    @Test
    void shouldReadTheElementsAsReceived() {
        assertThat(eval("{#authorizationDetails.raw.size()}")).isEqualTo(3);
        assertThat(eval("{#authorizationDetails.empty}")).isEqualTo(false);
    }

    private Object eval(String expression) {
        return templateEngine.getValue(expression, Object.class);
    }
}
