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

package io.gravitee.am.gateway.handler.oauth2.service.request;


import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class OAuth2RequestTest {

    @Test
    public void userApp_should_accept_openID_Scope_and_provide_idtoken() {
        final var request = new OAuth2Request();
        request.setScopes(Set.of(Scope.OPENID.getKey()));
        request.setSubject(UUID.randomUUID().toString());

        Assertions.assertTrue(request.shouldGenerateIDToken(false));
    }

    @Test
    public void clientOnly_should_reject_openID_Scope() {
        final var request = new OAuth2Request();
        request.setScopes(Set.of(Scope.OPENID.getKey()));
        request.setSubject(null);

        Assertions.assertThrows(InvalidScopeException.class, () -> request.shouldGenerateIDToken(false));
    }

    @Test
    public void clientOnly_should_ignore_IDToken_but_accept_OpenID_Scope() {
        final var request = new OAuth2Request();
        request.setScopes(Set.of(Scope.OPENID.getKey()));
        request.setSubject(null);

        Assertions.assertFalse(request.shouldGenerateIDToken(true));
    }

    @Test
    public void tokenExchange_should_not_generate_idtoken_when_requested_type_is_access_token() {
        final var request = new OAuth2Request();
        request.setScopes(Set.of(Scope.OPENID.getKey()));
        request.setSubject(UUID.randomUUID().toString());
        request.setIssuedTokenType(TokenType.ACCESS_TOKEN);

        Assertions.assertFalse(request.shouldGenerateIDToken(false));
    }

    @Test
    public void tokenExchange_should_generate_idtoken_when_requested_type_is_id_token() {
        final var request = new OAuth2Request();
        request.setScopes(Set.of(Scope.OPENID.getKey()));
        request.setSubject(UUID.randomUUID().toString());
        request.setIssuedTokenType(TokenType.ID_TOKEN);

        Assertions.assertTrue(request.shouldGenerateIDToken(false));
    }
}
