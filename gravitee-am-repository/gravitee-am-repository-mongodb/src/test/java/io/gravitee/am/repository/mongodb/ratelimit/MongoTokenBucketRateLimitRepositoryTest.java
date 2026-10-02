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
package io.gravitee.am.repository.mongodb.ratelimit;

import io.gravitee.repository.ratelimit.model.TokenBucket;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
public class MongoTokenBucketRateLimitRepositoryTest {

    private final MongoTokenBucketRateLimitRepository repository = new MongoTokenBucketRateLimitRepository();

    @Test
    public void shouldFailExplicitlyAsTheTokenBucketIsNotImplemented() {
        repository
                .refillAndTryConsume("key", 1, 10, 1000, 10, System.currentTimeMillis(), () -> new TokenBucket("key"))
                .test()
                .assertError(error -> error instanceof IllegalStateException && error.getMessage().contains("not implemented for MongoDB"));
    }
}
