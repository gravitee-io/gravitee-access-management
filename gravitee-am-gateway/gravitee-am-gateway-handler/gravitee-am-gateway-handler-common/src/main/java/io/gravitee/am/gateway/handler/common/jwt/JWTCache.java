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
package io.gravitee.am.gateway.handler.common.jwt;

import io.reactivex.rxjava3.core.Single;

public interface JWTCache {
    Single<Boolean> isPresent(String jwt);

    /**
     * Atomically records the jti. Returns {@code true} when it was newly added, {@code false} when
     * an entry was already present (i.e. a replay). Callers that need replay detection must rely on
     * this return value rather than a separate {@link #isPresent(String)} check, which is racy.
     */
    boolean put(String jwt, long expiresAt);

    class NoOpJtiCache implements JWTCache {

        @Override
        public Single<Boolean> isPresent(String jwt) {
            return Single.just(false);
        }

        @Override
        public boolean put(String jwt, long expiresAt) {
            return true;
        }
    }
}
