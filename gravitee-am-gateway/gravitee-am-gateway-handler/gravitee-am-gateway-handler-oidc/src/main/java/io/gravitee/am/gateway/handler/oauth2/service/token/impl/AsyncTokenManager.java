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
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author GraviteeSource Team
 */
public class AsyncTokenManager extends AbstractService implements TokenManager {

    private static final Logger logger = LoggerFactory.getLogger(AsyncTokenManager.class);

    private static final long DRAIN_TIMEOUT_MS = 5000;

    private final TokenRepository tokenRepository;
    private final FlowableProcessor<Completable> pendingWrites = PublishProcessor.<Completable>create().toSerialized();
    private final CompletableSubject drained = CompletableSubject.create();

    public AsyncTokenManager(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
        this.pendingWrites.flatMapCompletable(write -> write)
                .subscribe(drained::onComplete, drained::onError);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        pendingWrites.onComplete();
        if (!drained.blockingAwait(DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            logger.warn("Asynchronous token write(s) still pending after {}ms, they may be lost", DRAIN_TIMEOUT_MS);
        }
        super.doStop();
    }

    @Override
    public Completable storeTokens(AccessToken accessToken, RefreshToken refreshToken) {
        pendingWrites.onNext(tokenRepository.create(accessToken, refreshToken)
                .doOnError(error -> logger.error("Unable to store tokens asynchronously, domain={} client={} accessTokenJti={} refreshTokenJti={}",
                        accessToken.getDomain(), accessToken.getClient(), accessToken.getToken(),
                        refreshToken != null ? refreshToken.getToken() : null, error))
                .onErrorComplete());
        return Completable.complete();
    }
}
