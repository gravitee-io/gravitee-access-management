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
package io.gravitee.am.plugins.dataplane.core;

import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.CustomLog;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author GraviteeSource Team
 */
@CustomLog
public class DataPlaneVerifierImpl implements DataPlaneVerifier {

    private final long timeoutMillis;
    private final long retryAfterMillis;

    /** The providers under verification. An id absent from this and from {@link #reserved} is served without a check. */
    private final Map<String, DataPlaneProvider> required = new ConcurrentHashMap<>();

    /** Ids claimed but not yet given a provider, which are refused rather than served unchecked. */
    private final Set<String> reserved = ConcurrentHashMap.newKeySet();

    /**
     * The check itself, one per data plane, kept so that callers share it rather than each opening
     * their own connection. An entry means verified or being verified.
     */
    private final Map<String, Completable> checks = new ConcurrentHashMap<>();

    /** The last refusal per data plane, which callers are answered from until it goes stale. */
    private final Map<String, Failure> failures = new ConcurrentHashMap<>();

    /**
     * Which definition an id currently stands for, bumped every time one is put under verification.
     * A check that finishes after its own definition was forgotten or replaced is identified by a
     * stale stamp, so its result is dropped rather than recorded against the definition that
     * replaced it.
     */
    private final Map<String, Long> generations = new ConcurrentHashMap<>();

    private final AtomicLong generator = new AtomicLong();

    private record Failure(long at, Throwable cause) {}

    public DataPlaneVerifierImpl(long timeoutMillis, long retryAfterMillis) {
        this.timeoutMillis = timeoutMillis;
        this.retryAfterMillis = retryAfterMillis;
    }

    @Override
    public void reserve(String dataPlaneId) {
        reserved.add(dataPlaneId);
    }

    @Override
    public void require(String dataPlaneId, DataPlaneProvider provider) {
        generations.put(dataPlaneId, generator.incrementAndGet());
        required.put(dataPlaneId, provider);
        reserved.remove(dataPlaneId);
        verified(dataPlaneId)
                .subscribeOn(Schedulers.io())
                .subscribe(() -> {}, error -> {});
    }

    @Override
    public Completable verified(String dataPlaneId) {
        var provider = required.get(dataPlaneId);
        var generation = generations.get(dataPlaneId);
        if (provider == null || generation == null) {
            if (reserved.contains(dataPlaneId)) {
                return Completable.error(new IllegalStateException(
                        "Data plane [" + dataPlaneId + "] has not been checked yet"));
            }
            return Completable.complete();
        }

        var failure = failures.get(dataPlaneId);
        if (failure != null) {
            // a store that is down would otherwise take one connection attempt per caller, with no
            // backoff, at the point where it is already unhealthy
            if (System.currentTimeMillis() - failure.at() < retryAfterMillis) {
                return Completable.error(failure.cause());
            }
            failures.remove(dataPlaneId);
        }

        // the mapping function only builds the check, it does not run it: nothing blocks the map
        return checks.computeIfAbsent(dataPlaneId, id -> check(id, provider, generation).cache());
    }

    @Override
    public void forget(String dataPlaneId) {
        reserved.remove(dataPlaneId);
        required.remove(dataPlaneId);
        checks.remove(dataPlaneId);
        failures.remove(dataPlaneId);
        generations.remove(dataPlaneId);
    }

    private Completable check(String dataPlaneId, DataPlaneProvider provider, Long generation) {
        return Completable.defer(provider::healthCheck)
                // without this a caller waits on the driver's own server selection timeout
                .timeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .doOnComplete(() -> log.info("Data plane [{}] answered and can serve domains", dataPlaneId))
                .doOnError(error -> {
                    log.error("Data plane [{}] did not answer and cannot serve domains", dataPlaneId, error);
                    if (!Objects.equals(generations.get(dataPlaneId), generation)) {
                        // the definition this refusal belongs to has already been forgotten or replaced
                        return;
                    }
                    // dropping the check is what keeps a refusal from lasting until the next restart
                    failures.put(dataPlaneId, new Failure(System.currentTimeMillis(), error));
                    checks.remove(dataPlaneId);
                });
    }
}
