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
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Flowable;
import io.reactivex.processors.PublishProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenManagerImpl extends AbstractService implements TokenManager {

    private static final Logger logger = LoggerFactory.getLogger(TokenManagerImpl.class);
    private static final Integer bulkActions = 1000;
    private static final Long flushInterval = 1l;

    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private final PublishProcessor<AccessToken> bulkProcessorAccessToken = PublishProcessor.create();

    private final PublishProcessor<RefreshToken> bulkProcessorRefreshToken = PublishProcessor.create();

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // init bulk processors
        bulkProcessorAccessToken
                .buffer(
                        flushInterval,
                        TimeUnit.SECONDS,
                        bulkActions
                )
                .flatMap(this::bulkAccessTokens)
                .doOnError(throwable -> logger.error("An error occurs while store access tokens into MongoDB", throwable))
                .subscribe();

        // init bulk processors
        bulkProcessorRefreshToken
                .buffer(
                        flushInterval,
                        TimeUnit.SECONDS,
                        bulkActions
                )
                .flatMap(this::bulkRefreshTokens)
                .doOnError(throwable -> logger.error("An error occurs while store refresh tokens into MongoDB", throwable))
                .subscribe();
    }

    @Override
    public void storeAccessToken(AccessToken accessToken) {
        bulkProcessorAccessToken
                .onNext(accessToken);
    }

    @Override
    public void storeRefreshToken(RefreshToken refreshToken) {
        bulkProcessorRefreshToken
                .onNext(refreshToken);
    }

    private Flowable bulkAccessTokens(List<AccessToken> accessTokens) {
        if (accessTokens == null || accessTokens.isEmpty()) {
            return Flowable.empty();
        }

        return Flowable.fromPublisher(accessTokenRepository.bulkWrite(accessTokens));
    }

    private Flowable bulkRefreshTokens(List<RefreshToken> refreshTokens) {
        if (refreshTokens == null || refreshTokens.isEmpty()) {
            return Flowable.empty();
        }

        return Flowable.fromPublisher(refreshTokenRepository.bulkWrite(refreshTokens));
    }
}
