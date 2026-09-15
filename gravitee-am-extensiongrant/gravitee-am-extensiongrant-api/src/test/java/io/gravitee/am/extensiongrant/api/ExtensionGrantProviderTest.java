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
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionGrantProviderTest {

    @Test
    void shouldResolveEndUserFromGrantWithoutIdentityProvider() {
        User user = new DefaultUser("alice");
        ExtensionGrantProvider provider = tokenRequest -> Maybe.just(user);

        ResolvedEndUser resolved = provider.resolveEndUser(new TokenRequest()).blockingGet();

        assertSame(user, resolved.endUser());
        assertNull(resolved.identityProvider());
        assertTrue(resolved.verifiedClaims().isEmpty());
    }

    @Test
    void shouldResolveNothingWhenGrantReturnsNoUser() {
        ExtensionGrantProvider provider = tokenRequest -> Maybe.empty();

        provider.resolveEndUser(new TokenRequest()).test().assertComplete().assertNoValues();
    }
}
