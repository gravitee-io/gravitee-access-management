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

import static io.gravitee.am.repository.redis.ratelimit.RateLimitRepositoryConfiguration.SCRIPTS_RATELIMIT_LUA;
import static io.gravitee.am.repository.redis.ratelimit.RateLimitRepositoryConfiguration.SCRIPT_RATELIMIT_KEY;

import io.gravitee.am.repository.redis.common.RedisClient;
import io.gravitee.am.repository.redis.common.RedisConnectionFactory;
import io.gravitee.platform.repository.api.Scope;
import io.gravitee.am.repository.redis.ratelimit.RedisRateLimitRepository;
import io.vertx.core.Vertx;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * @author GraviteeSource Team
 */
@Configuration
@ComponentScan("io.gravitee.am.repository.redis")
public class RedisTestRepositoryConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(RedisTestRepositoryConfiguration.class);

    @Value("${redisStackVersion:6.2.6-v9}")
    private String redisStackVersion;

    @Bean(destroyMethod = "stop")
    public GenericContainer<?> redisContainer() {
        LOG.info("Creating redis container...");
        var redis = new GenericContainer<>(DockerImageName.parse("redis/redis-stack:" + redisStackVersion)).withExposedPorts(6379);

        LOG.info("Starting redis container...");
        redis.start();

        LOG.info("Running tests with redis version: {}", redisStackVersion);

        return redis;
    }

    @Bean(destroyMethod = "close")
    public Vertx vertx() {
        LOG.info("Creating vertx instance...");
        return Vertx.vertx();
    }

    @Bean
    public RedisClient redisRateLimitClient(GenericContainer<?> redisContainer, Vertx vertx) {
        String propertyPrefix = Scope.RATE_LIMIT.getName() + ".redis.";

        MockEnvironment mockEnvironment = new MockEnvironment();
        mockEnvironment.setProperty(propertyPrefix + "host", redisContainer.getHost());
        mockEnvironment.setProperty(propertyPrefix + "port", redisContainer.getFirstMappedPort().toString());

        LOG.info("Creating redis rate limit client factory");
        RedisConnectionFactory redisConnectionFactory = new RedisConnectionFactory(
                mockEnvironment,
                vertx,
                Scope.RATE_LIMIT.getName(),
                Map.of(SCRIPT_RATELIMIT_KEY, SCRIPTS_RATELIMIT_LUA)
        );

        LOG.info("Creating redis rate limit client");
        return redisConnectionFactory.createRedisClient();
    }

    @Bean
    public RedisRateLimitRepository redisRateLimitRepository(@Qualifier("redisRateLimitClient") RedisClient redisRateLimitClient) {
        LOG.info("Creating redis rate limit repository");
        return new RedisRateLimitRepository(redisRateLimitClient, 500);
    }
}