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
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Domain-level entry point for {@link InternalClientTokenIssuer}, so policies of any flow can resolve it.
 * The actual issuer lives in the OIDC protocol context and binds itself here on startup.
 */
public class DelegatingInternalClientTokenIssuer implements InternalClientTokenIssuer {

    private final AtomicReference<InternalClientTokenIssuer> delegate = new AtomicReference<>();

    public void bind(InternalClientTokenIssuer issuer) {
        delegate.set(issuer);
    }

    @Override
    public Single<IssuedToken> issue(Client client) {
        return Single.defer(() -> {
            InternalClientTokenIssuer issuer = delegate.get();
            if (issuer == null) {
                return Single.error(new IllegalStateException("Internal client token issuer is not available"));
            }
            return issuer.issue(client);
        });
    }
}
