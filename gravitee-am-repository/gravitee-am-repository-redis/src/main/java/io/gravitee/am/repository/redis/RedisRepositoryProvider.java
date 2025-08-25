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
package io.gravitee.am.repository.redis;

import io.gravitee.am.repository.redis.ratelimit.RateLimitRepositoryConfiguration;
import io.gravitee.platform.repository.api.RepositoryProvider;
import io.gravitee.platform.repository.api.Scope;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisRepositoryProvider implements RepositoryProvider {

    @Override
    public String type() {
        return "redis";
    }

    @Override
    public Scope[] scopes() {
        return new Scope[] { Scope.RATE_LIMIT };
    }

    @Override
    public Class<?> configuration(Scope scope) {
        log.info("Checking for Redis configuration for scope:");
        if (scope == Scope.RATE_LIMIT) {
            return RateLimitRepositoryConfiguration.class;
        }

        return null;
    }
}