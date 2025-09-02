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

package io.gravitee.repository.ratelimit.api;

import io.gravitee.repository.ratelimit.model.RateLimit;
import io.reactivex.rxjava3.core.Single;
import java.util.function.Supplier;

/**
 * @author GraviteeSource Team
 */
public interface RateLimitService {
    Single<RateLimit> incrementAndGet(String key, long weight, boolean async, Supplier<RateLimit> supplier);

    default Single<RateLimit> incrementAndGet(String key, boolean async, Supplier<RateLimit> supplier) {
        return incrementAndGet(key, 1, async, supplier);
    }
}