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
package io.gravitee.am.reporter.kafka;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class SchemaRegistryContainer extends GenericContainer<SchemaRegistryContainer> {
    public static final String SCHEMA_REGISTRY_IMAGE = "confluentinc/cp-schema-registry";
    public static final int SCHEMA_REGISTRY_PORT = 8081;

    public SchemaRegistryContainer(String version) {
        super(SCHEMA_REGISTRY_IMAGE + ":" + version);

        this.waitingFor(Wait.forHttp("/subjects").forStatusCode(200));
        this.withExposedPorts(SCHEMA_REGISTRY_PORT);
    }

    public SchemaRegistryContainer withKafka(KafkaContainer kafka) {
        String bootstrapServers = kafka.getNetworkAliases().get(0) + ":9092";
        this.withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry");
        this.withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081");
        this.withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://" + bootstrapServers);
        return self();
    }
}
