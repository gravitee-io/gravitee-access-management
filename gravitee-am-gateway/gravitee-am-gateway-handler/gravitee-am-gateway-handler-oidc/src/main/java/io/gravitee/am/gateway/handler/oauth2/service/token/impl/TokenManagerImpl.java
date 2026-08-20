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
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.api.BackwardCompatibleTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.am.repository.oauth2.model.Token;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenManagerImpl extends AbstractService implements TokenManager {

    private static final Logger logger = LoggerFactory.getLogger(TokenManagerImpl.class);

    private static final String ACCESS_TOKEN = "access token";
    private static final String REFRESH_TOKEN = "refresh token";
    private static final long DRAIN_TIMEOUT_MS = 5000;
    private static final long DRAIN_POLL_MS = 50;

    @Lazy
    @Autowired
    private BackwardCompatibleTokenRepository tokenRepository;

    @Setter
    @Value("${handlers.oauth2.tokens.asyncStore:false}")
    private boolean asyncStore;

    private final AtomicInteger pendingWrites = new AtomicInteger();

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        drainPendingWrites();
        super.doStop();
    }

    @Override
    public Completable storeAccessToken(AccessToken accessToken, Client client) {
        return store(tokenRepository.create(accessToken).ignoreElement(), accessToken, ACCESS_TOKEN, client);
    }

    @Override
    public Completable storeRefreshToken(RefreshToken refreshToken, Client client) {
        return store(tokenRepository.create(refreshToken).ignoreElement(), refreshToken, REFRESH_TOKEN, client);
    }

    private Completable store(Completable persist, Token token, String tokenType, Client client) {
        if (!isAsyncStoreEnabled(client)) {
            return persist;
        }
        pendingWrites.incrementAndGet();
        persist.subscribeOn(Schedulers.io())
                .doFinally(pendingWrites::decrementAndGet)
                .subscribe(() -> {
                }, error -> logger.error("Unable to store {} asynchronously, domain={} client={} jti={}",
                        tokenType, token.getDomain(), token.getClient(), token.getToken(), error));
        return Completable.complete();
    }

    private boolean isAsyncStoreEnabled(Client client) {
        return asyncStore || (client != null && Boolean.TRUE.equals(client.getAsyncTokenStore()));
    }

    private void drainPendingWrites() {
        final long deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS;
        while (pendingWrites.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(DRAIN_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        final int remaining = pendingWrites.get();
        if (remaining > 0) {
            logger.warn("{} asynchronous token write(s) still pending after {}ms, they may be lost", remaining, DRAIN_TIMEOUT_MS);
        }
    }
}
