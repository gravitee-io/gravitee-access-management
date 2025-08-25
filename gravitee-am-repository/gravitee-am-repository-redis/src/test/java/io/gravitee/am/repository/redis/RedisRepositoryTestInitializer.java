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

import io.gravitee.am.repository.RepositoriesTestInitializer;
import io.gravitee.am.repository.redis.common.RedisClient;
import io.vertx.redis.client.Command;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RedisRepositoryTestInitializer implements RepositoriesTestInitializer {

    private final List<RedisClient> redisClients;

    @Autowired
    public RedisRepositoryTestInitializer(List<RedisClient> redisClients) {
        this.redisClients = redisClients;
    }

    @Override
    public void before(Class testClass) {
        log.info("Redis test repository initialization");
        // Wait for all RedisApi to be ready
        redisClients.forEach(redisClient -> {
            try {
                log.info("Waiting for RedisApi to be ready");
                redisClient.redisApi().toCompletionStage().toCompletableFuture().get(1500, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("Error while waiting for RedisApi to be ready", e);
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public void after(Class testClass) {
        log.info("Redis test repository cleanup");
        redisClients.forEach(redisClient -> {
            try {
                log.info("Waiting for RedisApi to be cleaned");
                redisClient
                        .redisApi()
                        .flatMap(redisAPI ->
                                redisAPI
                                        .send(Command.KEYS, "*")
                                        .onSuccess(event ->
                                                event
                                                        .iterator()
                                                        .forEachRemaining(key ->
                                                                redisAPI
                                                                        .send(Command.DEL, key.toString())
                                                                        .onFailure(t -> log.error("unable to delete key {}.", key, t))
                                                                        .result()
                                                        )
                                        )
                        )
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(500, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("Error while waiting for RedisApi to be cleaned", e);
                Thread.currentThread().interrupt();
            }
        });
    }
}