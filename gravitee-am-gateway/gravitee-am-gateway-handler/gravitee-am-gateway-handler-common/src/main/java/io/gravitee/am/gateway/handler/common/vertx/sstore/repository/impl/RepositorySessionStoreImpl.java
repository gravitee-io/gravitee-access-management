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
package io.gravitee.am.gateway.handler.common.vertx.sstore.repository.impl;

import io.gravitee.am.gateway.handler.common.vertx.sstore.repository.RepositorySessionStore;
import io.gravitee.am.repository.management.api.SessionRepository;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.sstore.SessionStore;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RepositorySessionStoreImpl implements RepositorySessionStore  {

    private PRNG random;
    private SessionRepository sessionRepository;
    private final long retryTimeout;

    public RepositorySessionStoreImpl(Vertx vertx, long retryTimeout, SessionRepository sessionRepository) {
        // initialize a secure random
        this.random = new PRNG(vertx);
        this.sessionRepository = sessionRepository;
        this.retryTimeout = retryTimeout;
    }

    @Override
    public SessionStore init(Vertx vertx, JsonObject options) {
        return this;
    }

    @Override
    public long retryTimeout() {
        return retryTimeout;
    }

    @Override
    public Session createSession(long timeout) {
        return createSession(timeout, DEFAULT_SESSIONID_LENGTH);
    }

    @Override
    public Session createSession(long timeout, int length) {
        return new RepositorySession(random, timeout, length);
    }

    @Override
    public void get(String cookieValue, Handler<AsyncResult<Session>> resultHandler) {
        sessionRepository.findById(cookieValue)
                .subscribe(
                        session -> {
                            // reconstruct the session
                            RepositorySession repositorySession = new RepositorySession(random);
                            repositorySession.readFromBuffer(0, Buffer.buffer(session.getValue()));
                            // need to validate for expired
                            long now = System.currentTimeMillis();
                            // if expired, the operation succeeded, but returns null
                            if (now - repositorySession.lastAccessed() > repositorySession.timeout()) {
                                resultHandler.handle(Future.succeededFuture());
                            } else {
                                resultHandler.handle(Future.succeededFuture(repositorySession));
                            }
                        },
                        e -> resultHandler.handle(Future.failedFuture(e)),
                        () -> resultHandler.handle(Future.succeededFuture()));
    }

    @Override
    public void delete(String id, Handler<AsyncResult<Void>> resultHandler) {
        sessionRepository.delete(id)
                .subscribe(
                        () -> resultHandler.handle(Future.succeededFuture()),
                        error -> resultHandler.handle(Future.failedFuture(error)));

    }

    @Override
    public void put(Session session, Handler<AsyncResult<Void>> resultHandler) {
        final RepositorySession newSession = (RepositorySession) session;
        sessionRepository.findById(session.id())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMapSingle(optionalSession -> {
                    // old session exists, we need to validate versions
                    if (optionalSession.isPresent()) {
                        io.gravitee.am.model.Session storedSession = optionalSession.get();
                        RepositorySession oldSession = new RepositorySession(random);
                        oldSession.readFromBuffer(0, Buffer.buffer(storedSession.getValue()));
                        if (oldSession.version() != newSession.version()) {
                            return Single.error(new IllegalStateException("Session version mismatch"));
                        }
                    }

                    // write session in the repository
                    Instant now = Instant.now();
                    newSession.incrementVersion();

                    io.gravitee.am.model.Session sessionToCreate = new io.gravitee.am.model.Session();
                    sessionToCreate.setId(newSession.id());
                    sessionToCreate.setValue(getSessionData(newSession));
                    sessionToCreate.setCreatedAt(new Date(now.toEpochMilli()));
                    sessionToCreate.setUpdatedAt(sessionToCreate.getCreatedAt());
                    sessionToCreate.setExpireAt(new Date(now.plusMillis(newSession.timeout()).toEpochMilli()));
                    return sessionRepository.update(sessionToCreate);
                })
                .subscribe(
                        __ ->  resultHandler.handle(Future.succeededFuture()),
                        error -> resultHandler.handle(Future.failedFuture(error.getMessage())));
    }

    @Override
    public void clear(Handler<AsyncResult<Void>> resultHandler) {
        sessionRepository.clear()
                .subscribe(
                        () -> resultHandler.handle(Future.succeededFuture()),
                        error -> resultHandler.handle(Future.failedFuture(error)));

    }

    @Override
    public void size(Handler<AsyncResult<Integer>> resultHandler) {
        sessionRepository.count()
                .subscribe(
                        result -> {
                            long lngCount = result;
                            int count = (lngCount > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) lngCount;
                            resultHandler.handle(Future.succeededFuture(count));
                        },
                        error ->  resultHandler.handle(Future.failedFuture(error)));
    }

    @Override
    public void close() {
        // stop seeding the PRNG
        random.close();
    }

    private byte[] getSessionData(RepositorySession repositorySession) {
        Buffer buffer = Buffer.buffer();
        repositorySession.writeToBuffer(buffer);
        return buffer.getBytes();
    }
}
