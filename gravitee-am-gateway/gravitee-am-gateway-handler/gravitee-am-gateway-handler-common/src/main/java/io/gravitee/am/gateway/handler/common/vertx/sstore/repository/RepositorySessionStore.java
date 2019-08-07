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
package io.gravitee.am.gateway.handler.common.vertx.sstore.repository;

import io.gravitee.am.gateway.handler.common.vertx.sstore.repository.impl.RepositorySessionStoreImpl;
import io.gravitee.am.repository.management.api.SessionRepository;
import io.vertx.core.Vertx;
import io.vertx.ext.web.sstore.SessionStore;

/**
 * A session store based on an underlying repository
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface RepositorySessionStore extends SessionStore {
    long DEFAULT_RETRY_TIMEOUT_MS = 5 * 1000;

    /**
     * Creates a RepositorySessionStore with the default retry TO.
     *
     * @param vertx a Vert.x instance
     * @param sessionRepository an underlying repository
     * @return the store
     */
    static RepositorySessionStore create(Vertx vertx, SessionRepository sessionRepository) {
        return new RepositorySessionStoreImpl(vertx, DEFAULT_RETRY_TIMEOUT_MS, sessionRepository);
    }

    /**
     * Creates a RepositorySessionStore with the given retry TO.
     *
     * @param vertx a Vert.x instance
     * @param sessionRepository an underlying repository
     * @return the store
     */
    static RepositorySessionStore create(Vertx vertx, long retryTimeoutMs, SessionRepository sessionRepository) {
        return new RepositorySessionStoreImpl(vertx, retryTimeoutMs, sessionRepository);
    }
}
