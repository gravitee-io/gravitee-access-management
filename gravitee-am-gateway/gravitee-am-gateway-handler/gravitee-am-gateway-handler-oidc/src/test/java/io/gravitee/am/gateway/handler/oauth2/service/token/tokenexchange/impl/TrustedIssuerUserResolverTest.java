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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserBindingCriterion;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustedIssuerUserResolverTest {

    @Mock
    private UserGatewayService userGatewayService;

    private TrustedIssuerUserResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TrustedIssuerUserResolver(userGatewayService);
    }

    private ValidatedToken subjectTokenWith(TrustedIssuer trusted) {
        return ValidatedToken.builder()
                .subject("sub-1")
                .claims(Map.of("email", "user@example.com", "preferred_username", "joe"))
                .trustedIssuer(trusted)
                .build();
    }

    @Test
    void resolve_returnsEmptyWhenUserBindingDisabled() {
        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(false);

        User result = resolver.resolve(subjectTokenWith(trusted)).blockingGet();

        assertThat(result).isNull();
    }

    @Test
    void resolve_returnsEmptyWhenTrustedIssuerNull() {
        User result = resolver.resolve(subjectTokenWith(null)).blockingGet();

        assertThat(result).isNull();
    }

    @Test
    void resolve_returnsEmptyWhenNoCriteria() {
        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(true);
        trusted.setUserBindingCriteria(Collections.emptyList());

        User result = resolver.resolve(subjectTokenWith(trusted)).blockingGet();

        assertThat(result).isNull();
    }

    @Test
    void resolve_throwsWhenZeroUsersMatch() {
        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(true);
        UserBindingCriterion c = new UserBindingCriterion();
        c.setAttribute("emails.value");
        c.setExpression("{#token['email']}");
        trusted.setUserBindingCriteria(List.of(c));

        when(userGatewayService.findByCriteria(any())).thenReturn(Single.just(List.of()));

        assertThatThrownBy(() -> resolver.resolve(subjectTokenWith(trusted)).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("No domain user found for token binding");
    }

    @Test
    void resolve_returnsUserWhenOneMatch() {
        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(true);
        UserBindingCriterion c = new UserBindingCriterion();
        c.setAttribute("emails.value");
        c.setExpression("{#token['email']}");
        trusted.setUserBindingCriteria(List.of(c));

        User domainUser = new User();
        domainUser.setId("domain-user-id");
        when(userGatewayService.findByCriteria(any())).thenReturn(Single.just(List.of(domainUser)));

        User result = resolver.resolve(subjectTokenWith(trusted)).blockingGet();

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("domain-user-id");
    }

    @Test
    void resolve_throwsWhenMultipleUsersMatch() {
        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(true);
        UserBindingCriterion c = new UserBindingCriterion();
        c.setAttribute("emails.value");
        c.setExpression("{#token['email']}");
        trusted.setUserBindingCriteria(List.of(c));

        User u1 = new User();
        User u2 = new User();
        when(userGatewayService.findByCriteria(any())).thenReturn(Single.just(List.of(u1, u2)));

        assertThatThrownBy(() -> resolver.resolve(subjectTokenWith(trusted)).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Multiple domain users match token binding");
    }

    @Test
    void resolve_throwsWhenNullClaims() {
        // T3: ValidatedToken with null claims should produce InvalidRequestException
        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(true);
        UserBindingCriterion c = new UserBindingCriterion();
        c.setAttribute("emails.value");
        c.setExpression("{#token['email']}");
        trusted.setUserBindingCriteria(List.of(c));

        ValidatedToken noClaims = ValidatedToken.builder()
                .subject("sub-1")
                .claims(null)
                .trustedIssuer(trusted)
                .build();

        assertThatThrownBy(() -> resolver.resolve(noClaims).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no claims available");
    }

    @Test
    void resolve_throwsWhenExpressionEvaluatesToWhitespace() {
        // T4: EL expression evaluating to whitespace should produce InvalidRequestException
        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(true);
        UserBindingCriterion c = new UserBindingCriterion();
        c.setAttribute("emails.value");
        c.setExpression("{#token['email']}");
        trusted.setUserBindingCriteria(List.of(c));

        ValidatedToken whitespaceToken = ValidatedToken.builder()
                .subject("sub-1")
                .claims(Map.of("email", "   "))
                .trustedIssuer(trusted)
                .build();

        assertThatThrownBy(() -> resolver.resolve(whitespaceToken).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("evaluated to empty value");
    }

    @Test
    void resolve_throwsWhenExpressionEvaluatesToNull() {
        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(true);
        UserBindingCriterion c = new UserBindingCriterion();
        c.setAttribute("emails.value");
        c.setExpression("{#token['nonexistent']}");
        trusted.setUserBindingCriteria(List.of(c));

        assertThatThrownBy(() -> resolver.resolve(subjectTokenWith(trusted)).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("evaluated to null");
    }
}
