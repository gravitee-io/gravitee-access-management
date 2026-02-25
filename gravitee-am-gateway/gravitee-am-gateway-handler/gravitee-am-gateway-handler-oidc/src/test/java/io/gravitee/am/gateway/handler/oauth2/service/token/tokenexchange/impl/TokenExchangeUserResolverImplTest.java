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

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenExchangeUserResolverImplTest {

    @Mock
    private UserGatewayService userGatewayService;

    private TokenExchangeUserResolverImpl resolver;
    private ValidatedToken subjectToken;

    @BeforeEach
    void setUp() {
        resolver = new TokenExchangeUserResolverImpl();
        subjectToken = ValidatedToken.builder()
                .subject("sub-1")
                .claims(Map.of("email", "user@example.com", "preferred_username", "joe"))
                .build();
    }

    @Test
    void resolve_returnsEmptyWhenUserBindingDisabled() {
        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(false);

        Optional<User> result = resolver.resolve(subjectToken, trusted, userGatewayService).blockingGet();

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_returnsEmptyWhenTrustedIssuerNull() {
        Optional<User> result = resolver.resolve(subjectToken, null, userGatewayService).blockingGet();

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_returnsEmptyWhenNoCriteria() {
        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(true);
        trusted.setUserBindingCriteria(Collections.emptyList());

        Optional<User> result = resolver.resolve(subjectToken, trusted, userGatewayService).blockingGet();

        assertThat(result).isEmpty();
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

        assertThatThrownBy(() -> resolver.resolve(subjectToken, trusted, userGatewayService).blockingGet())
                .isInstanceOf(InvalidGrantException.class)
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

        Optional<User> result = resolver.resolve(subjectToken, trusted, userGatewayService).blockingGet();

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("domain-user-id");
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

        assertThatThrownBy(() -> resolver.resolve(subjectToken, trusted, userGatewayService).blockingGet())
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("Multiple domain users match token binding");
    }

    @Test
    void resolve_throwsWhenNullClaims() {
        // T3: ValidatedToken with null claims should produce InvalidGrantException
        ValidatedToken noClaims = ValidatedToken.builder()
                .subject("sub-1")
                .claims(null)
                .build();

        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(true);
        UserBindingCriterion c = new UserBindingCriterion();
        c.setAttribute("emails.value");
        c.setExpression("{#token['email']}");
        trusted.setUserBindingCriteria(List.of(c));

        assertThatThrownBy(() -> resolver.resolve(noClaims, trusted, userGatewayService).blockingGet())
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("no claims available");
    }

    @Test
    void resolve_throwsWhenExpressionEvaluatesToWhitespace() {
        // T4: EL expression evaluating to whitespace should produce InvalidGrantException
        ValidatedToken whitespaceToken = ValidatedToken.builder()
                .subject("sub-1")
                .claims(Map.of("email", "   "))
                .build();

        TrustedIssuer trusted = new TrustedIssuer();
        trusted.setUserBindingEnabled(true);
        UserBindingCriterion c = new UserBindingCriterion();
        c.setAttribute("emails.value");
        c.setExpression("{#token['email']}");
        trusted.setUserBindingCriteria(List.of(c));

        assertThatThrownBy(() -> resolver.resolve(whitespaceToken, trusted, userGatewayService).blockingGet())
                .isInstanceOf(InvalidGrantException.class)
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

        assertThatThrownBy(() -> resolver.resolve(subjectToken, trusted, userGatewayService).blockingGet())
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("evaluated to null");
    }
}
