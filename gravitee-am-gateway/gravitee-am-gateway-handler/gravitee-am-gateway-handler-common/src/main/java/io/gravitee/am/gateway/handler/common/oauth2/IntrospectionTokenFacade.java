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
package io.gravitee.am.gateway.handler.common.oauth2;

import io.gravitee.am.common.jwt.JWT;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IntrospectionTokenFacade {
    private final IntrospectionTokenService accessTokenIntrospectionTokenService;
    private final IntrospectionTokenService refreshTokenIntrospectionTokenService;

    public Maybe<JWT> introspectAccessToken(String token) {
        return introspectAccessToken(token, null);
    }

    public Maybe<JWT> introspectRefreshToken(String token) {
        return introspectRefreshToken(token, null);
    }

    public Maybe<JWT> introspectAccessToken(String token, String callerClientId) {
        return accessTokenIntrospectionTokenService.introspect(token, false, callerClientId)
                .onErrorResumeWith(Maybe.empty());
    }

    public Maybe<JWT> introspectRefreshToken(String token, String callerClientId) {
        return refreshTokenIntrospectionTokenService.introspect(token, false, callerClientId)
                .onErrorResumeWith(Maybe.empty());
    }
}
