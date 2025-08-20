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
package io.gravitee.am.repository.redis.ratelimit;

import io.gravitee.am.repository.redis.common.RedisConnectionFactory;
import io.gravitee.am.repository.redis.vertx.RedisClient;
import io.gravitee.platform.repository.api.Scope;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RateLimitRepositoryConfiguration {

    private final Logger LOGGER = LoggerFactory.getLogger(RateLimitRepositoryConfiguration.class);

    public static final String SCRIPT_RATELIMIT_KEY = "ratelimit";
    public static final String SCRIPTS_RATELIMIT_LUA = "scripts/ratelimit/ratelimit.lua";

    @Bean("redisRateLimitClient")
    public RedisClient redisRedisClient(Environment environment) {
        LOGGER.info("Creating redis rate limit client");
        return new RedisConnectionFactory(
                environment,
                Vertx.vertx(),
                Scope.RATE_LIMIT.getName(),
                Map.of(SCRIPT_RATELIMIT_KEY, SCRIPTS_RATELIMIT_LUA)
        )
                .createRedisClient();
    }

    @Bean
    public RedisRateLimitRepository redisRateLimitRepository(
            @Qualifier("redisRateLimitClient") RedisClient redisClient,
            @Value("${ratelimit.redis.operation.timeout:10}") int operationTimeout
    ) {
        LOGGER.info("Creating redis rate limit repository");
        return new RedisRateLimitRepository(redisClient, operationTimeout);
    }
}
