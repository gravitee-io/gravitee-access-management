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

import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeUserResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TokenExchangeUserResolverFacade implements TokenExchangeUserResolver {
    private final TokenUserResolver tokenUserResolver;
    private final TrustedIssuerUserResolver trustedIssuerUserResolver;

    @Override
    public Maybe<User> resolve(ValidatedToken subjectToken) {
        return trustedIssuerUserResolver.resolve(subjectToken)
                .switchIfEmpty(tokenUserResolver.resolve(subjectToken));
    }
}
