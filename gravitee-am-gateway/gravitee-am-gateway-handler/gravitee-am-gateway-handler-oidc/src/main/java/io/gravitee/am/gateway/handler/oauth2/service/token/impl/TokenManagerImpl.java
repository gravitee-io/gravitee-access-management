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
import io.gravitee.am.gateway.handler.oauth2.service.token.indexer.AccessTokenBulkProcessor;
import io.gravitee.am.gateway.handler.oauth2.service.token.indexer.RefreshTokenBulkProcessor;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.common.service.AbstractService;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenManagerImpl extends AbstractService implements TokenManager {

    private static final Integer bulkActions = 1000;
    private static final Long flushInterval = 1l;

    @Lazy
    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Lazy
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private final PublishProcessor<AccessToken> bulkProcessorAccessToken = PublishProcessor.create();

    private final PublishProcessor<RefreshToken> bulkProcessorRefreshToken = PublishProcessor.create();

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // init bulk processors
        bulkProcessorAccessToken
            .onBackpressureBuffer()
            .observeOn(Schedulers.io())
            .buffer(flushInterval, TimeUnit.SECONDS, bulkActions)
            .filter(accessTokens -> accessTokens != null && !accessTokens.isEmpty())
            .subscribe(new AccessTokenBulkProcessor(accessTokenRepository));

        // init bulk processors
        bulkProcessorRefreshToken
            .onBackpressureBuffer()
            .observeOn(Schedulers.io())
            .buffer(flushInterval, TimeUnit.SECONDS, bulkActions)
            .filter(refreshTokens -> refreshTokens != null && !refreshTokens.isEmpty())
            .subscribe(new RefreshTokenBulkProcessor(refreshTokenRepository));
    }

    @Override
    public void storeAccessToken(AccessToken accessToken) {
        bulkProcessorAccessToken.onNext(accessToken);
    }

    @Override
    public void storeRefreshToken(RefreshToken refreshToken) {
        bulkProcessorRefreshToken.onNext(refreshToken);
    }
}
