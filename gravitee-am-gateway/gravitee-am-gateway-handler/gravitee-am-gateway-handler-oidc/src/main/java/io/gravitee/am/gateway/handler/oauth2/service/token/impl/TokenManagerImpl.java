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
package io.gravitee.am.gateway.handler.oauth2.service.token.impl;

import io.gravitee.am.gateway.handler.oauth2.service.token.TokenManager;
import io.gravitee.am.repository.oauth2.api.BackwardCompatibleTokenRepository;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenManagerImpl extends AbstractService implements TokenManager {

    @Lazy
    @Autowired
    private BackwardCompatibleTokenRepository tokenRepository;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public Completable storeAccessToken(AccessToken accessToken) {
        return tokenRepository.create(accessToken).ignoreElement();
    }

    @Override
    public Completable storeRefreshToken(RefreshToken refreshToken) {
        return tokenRepository.create(refreshToken).ignoreElement();
    }
}
