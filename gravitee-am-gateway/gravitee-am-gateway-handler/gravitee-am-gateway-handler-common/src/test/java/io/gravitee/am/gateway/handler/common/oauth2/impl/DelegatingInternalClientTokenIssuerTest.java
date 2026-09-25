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
package io.gravitee.am.gateway.handler.common.oauth2.impl;

import io.gravitee.am.gateway.handler.common.oauth2.InternalClientTokenIssuer;
import io.gravitee.am.gateway.handler.common.oauth2.InternalClientTokenIssuer.IssuedToken;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DelegatingInternalClientTokenIssuerTest {

    private final DelegatingInternalClientTokenIssuer issuer = new DelegatingInternalClientTokenIssuer();
    private final Client client = new Client();

    @Test
    void shouldFailWhenNoIssuerBound() {
        issuer.issue(client).test()
                .assertError(e -> e instanceof IllegalStateException && e.getMessage().equals("Internal client token issuer is not available"));
    }

    @Test
    void shouldDelegateToBoundIssuer() {
        IssuedToken token = new IssuedToken("token", 3600);
        issuer.bind(boundIssuer(token));

        issuer.issue(client).test().assertValue(token);
    }

    private InternalClientTokenIssuer boundIssuer(IssuedToken token) {
        InternalClientTokenIssuer bound = mock(InternalClientTokenIssuer.class);
        when(bound.issue(client)).thenReturn(Single.just(token));
        return bound;
    }
}
