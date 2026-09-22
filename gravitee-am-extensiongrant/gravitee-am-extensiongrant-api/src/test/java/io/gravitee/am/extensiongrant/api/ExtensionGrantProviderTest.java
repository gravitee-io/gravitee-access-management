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
package io.gravitee.am.extensiongrant.api;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionGrantProviderTest {

    private static final ExtensionGrantRequest REQUEST =
            ExtensionGrantRequest.builder().clientId("client-id").authorizationServerIssuer("https://as.example.com/domain/oidc").build();

    @Test
    void shouldResolveEndUserFromGrantWithoutIdentityProvider() {
        User user = new DefaultUser("alice");
        ExtensionGrantResult result = ExtensionGrantResult.endUser(user);
        ExtensionGrantProvider provider = request -> Maybe.just(result);

        ExtensionGrantResult resolved = provider.grant(REQUEST).blockingGet();

        assertSame(user, resolved.endUser());
        assertNull(resolved.identityProvider());
        assertTrue(resolved.verifiedClaims().isEmpty());
        assertTrue(resolved.bindingCriteria().isEmpty());
        assertNull(resolved.resource());
        assertNull(resolved.scopes());
    }

    @Test
    void shouldSupportRefreshTokenByDefault() {
        ExtensionGrantProvider provider = request -> Maybe.empty();

        assertTrue(provider.supportsRefreshToken());
    }

    @Test
    void shouldResolveNothingWhenGrantReturnsNoUser() {
        ExtensionGrantProvider provider = request -> Maybe.empty();

        provider.grant(REQUEST).test().assertComplete().assertNoValues();
    }
}
