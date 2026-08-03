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
package io.gravitee.am.gateway.handler.common.dpop;

import io.reactivex.rxjava3.core.Single;

/**
 * Single-use registry for DPoP proof {@code jti} claims (RFC 9449). Backed by the node cache, so the
 * registry spans the cluster when the node runs a distributed cache.
 *
 * <p>Implementations differ only in how they claim a key atomically: in-memory caches provide
 * {@code computeIfAbsent}, while the Redis cache rejects it and exposes SETNX through its
 * two-argument {@code put}. The factory selects one from the configured cache type. Do not probe the
 * backend by catching {@code UnsupportedOperationException} — that costs a thrown exception on every
 * request.
 */
public interface ReplayCache {

    /**
     * Claim a {@code jti} for its first and only use.
     *
     * @return {@code true} when this call won the claim, {@code false} when the proof is a replay
     */
    Single<Boolean> register(String jti);

    class NoOpReplayCache implements ReplayCache {

        @Override
        public Single<Boolean> register(String jti) {
            return Single.just(true);
        }
    }
}
