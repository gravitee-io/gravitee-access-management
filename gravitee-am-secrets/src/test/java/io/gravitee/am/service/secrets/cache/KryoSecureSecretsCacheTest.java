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
package io.gravitee.am.service.secrets.cache;

import com.google.common.collect.ArrayListMultimap;
import io.gravitee.secrets.api.core.Secret;
import io.gravitee.secrets.api.core.SecretURL;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * @author GraviteeSource Team
 */
public class KryoSecureSecretsCacheTest {

    private static final Duration SHORT_CACHE_TTL = Duration.ofMillis(1);
    private static final Duration CLEANUP_ENFORCE_RATE = Duration.ofMillis(10);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @Test
    public void shouldCacheSecretValue() {
        SecretsCache cache = new KryoSecureSecretsCache(null, null);

        SecretURL secretURL = new SecretURL("validProvider", "secretPath", "secretKey", ArrayListMultimap.create(), false);
        assertThat(cache.get(secretURL)).isEmpty();

        cache.put(secretURL, new Secret("secretValue"));
        assertThat(cache.get(secretURL)).isEqualTo(Optional.of(new Secret("secretValue")));
    }

    @Test
    public void shouldEvictSecretValueAfterTtlPasses() {
        SecretsCache cache = new KryoSecureSecretsCache(SHORT_CACHE_TTL, null);
        scheduleEnforcedCleanup(cache);

        SecretURL secretURL = new SecretURL("validProvider", "secretPath", "secretKey", ArrayListMultimap.create(), false);

        cache.put(secretURL, new Secret("secretValue"));
        await().atMost(5, TimeUnit.SECONDS).until(() -> cache.get(secretURL).isEmpty());
    }

    @Test
    public void shouldEvictSecretValueAfterMaxSizeReached() {
        SecretsCache cache = new KryoSecureSecretsCache(null, 1L);
        scheduleEnforcedCleanup(cache);

        SecretURL secretUrl1 = new SecretURL("validProvider", "secretPath", "secretKey1", ArrayListMultimap.create(), false);
        SecretURL secretUrl2 = new SecretURL("validProvider", "secretPath", "secretKey2", ArrayListMultimap.create(), false);

        cache.put(secretUrl1, new Secret("secretValue1"));
        cache.put(secretUrl2, new Secret("secretValue2"));

        await().atMost(5, TimeUnit.SECONDS).until(() -> cache.get(secretUrl1).isEmpty());
        assertThat(cache.get(secretUrl2)).isPresent();
    }

    private void scheduleEnforcedCleanup(SecretsCache cache) {
        executor.scheduleAtFixedRate(
                cache::triggerCleanUp,
                0,
                CLEANUP_ENFORCE_RATE.getNano(),
                TimeUnit.NANOSECONDS
        );
    }
}
