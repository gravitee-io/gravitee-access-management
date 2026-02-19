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

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenService;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionResult;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceSyncService;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.core.env.Environment;

import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.REFRESH_TOKEN;

public class IntrospectionRefreshTokenService extends BaseIntrospectionTokenService implements IntrospectionTokenService {
    private final TokenRepository tokenRepository;

    public IntrospectionRefreshTokenService(JWTService jwtService,
                                            ClientSyncService clientService,
                                            ProtectedResourceManager protectedResourceManager,
                                            ProtectedResourceSyncService protectedResourceSyncService,
                                            Environment environment,
                                            TokenRepository tokenRepository) {
        super(REFRESH_TOKEN, jwtService, clientService, protectedResourceManager, protectedResourceSyncService, environment);
        this.tokenRepository = tokenRepository;
    }

    @Override
    protected Maybe<RefreshToken> findByToken(String token) {
        return tokenRepository.findRefreshTokenByJti(token);
    }

    @Override
    public Maybe<IntrospectionResult> introspect(String token, boolean offlineVerification, String callerClientId) {
        return introspectToken(token, offlineVerification, callerClientId);
    }
}
