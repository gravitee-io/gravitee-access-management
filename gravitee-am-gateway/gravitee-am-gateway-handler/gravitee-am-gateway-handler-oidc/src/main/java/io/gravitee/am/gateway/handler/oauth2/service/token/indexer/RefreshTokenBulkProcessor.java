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
package io.gravitee.am.gateway.handler.oauth2.service.token.indexer;

import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RefreshTokenBulkProcessor implements Subscriber<List<RefreshToken>> {

    private final Logger logger = LoggerFactory.getLogger(RefreshTokenBulkProcessor.class);
    private Subscription subscription;
    private RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenBulkProcessor(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(List<RefreshToken> refreshTokens) {
        refreshTokenRepository.bulkWrite(refreshTokens)
                .retryWhen(t -> t.take(30).delay(1000, TimeUnit.MILLISECONDS))
                .subscribe(
                        () -> logger.debug("Refresh tokens indexation completed"),
                        error -> logger.error("Unexpected error while indexing refresh tokens", error));
        subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
        logger.error("Unexpected error while indexing refresh tokens", throwable);
    }

    @Override
    public void onComplete() {
        // Nothing to do here
    }
}
