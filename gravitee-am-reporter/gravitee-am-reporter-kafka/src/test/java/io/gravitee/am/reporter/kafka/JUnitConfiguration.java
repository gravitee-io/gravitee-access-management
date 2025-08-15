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

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.common.utils.WriteStreamRegistry;
import io.gravitee.node.api.Node;
import io.vertx.core.Vertx;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.Mockito.when;

/**
 * @author Florent Amaridon
 * @author Visiativ
 */
@Configuration
@ComponentScan("io.gravitee.am.reporter.kafka")
public class JUnitConfiguration implements ApplicationListener<ContextClosedEvent> {

    private KafkaContainer kafkaContainer;
    private SchemaRegistryContainer schemaRegistryContainer;
    private static final Network NETWORK = Network.newNetwork();
    private static final String CONFLUENT_PLATFORM_VERSION = "7.6.1";

    @Bean
    public KafkaContainer kafkaContainer() {
        kafkaContainer = new KafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:" + CONFLUENT_PLATFORM_VERSION))
                .withNetwork(NETWORK);
        kafkaContainer.start();
        return kafkaContainer;
    }

    @Bean
    public SchemaRegistryContainer schemaRegistryContainer(KafkaContainer kafkaContainer) {
        schemaRegistryContainer = new SchemaRegistryContainer(CONFLUENT_PLATFORM_VERSION);
        schemaRegistryContainer.withNetwork(NETWORK).withKafka(kafkaContainer).start();
        return schemaRegistryContainer;
    }

    @Bean("config")
    public KafkaReporterConfiguration configuration1(KafkaContainer kafkaContainer) {
        KafkaReporterConfiguration kafkaReporterConfiguration = new KafkaReporterConfiguration();
        kafkaReporterConfiguration.setTopic("gravitee-audit");
        kafkaReporterConfiguration.setBootstrapServers(kafkaContainer.getBootstrapServers());
        kafkaReporterConfiguration.setAcks("1");
        return kafkaReporterConfiguration;
    }

    @Bean("configWithSchemaRegistry")
    public KafkaReporterConfiguration configuration2(KafkaContainer kafkaContainer) {
        KafkaReporterConfiguration kafkaReporterConfiguration = new KafkaReporterConfiguration();
        kafkaReporterConfiguration.setTopic("gravitee-audit-sr");
        kafkaReporterConfiguration.setBootstrapServers(kafkaContainer.getBootstrapServers());
        kafkaReporterConfiguration.setAcks("1");
        kafkaReporterConfiguration.setSchemaRegistryUrl("http://" + schemaRegistryContainer.getHost() + ":" + schemaRegistryContainer.getFirstMappedPort());
        return kafkaReporterConfiguration;
    }


    @Override
    public void onApplicationEvent(@NotNull ContextClosedEvent contextClosedEvent) {
        if (this.kafkaContainer != null) {
            this.kafkaContainer.stop();
        }
        if (this.schemaRegistryContainer != null) {
            this.schemaRegistryContainer.stop();
        }
    }

    @Bean
    public GraviteeContext graviteeContext() {
        return GraviteeContext.defaultContext("domain");
    }

    @Bean
    public Vertx vertx() {
        return Vertx.vertx();
    }

    @Bean
    public Node getNode() {
        Node node = Mockito.mock(Node.class);
        when(node.hostname()).thenReturn("main.srv.local");
        when(node.id()).thenReturn("node-id");
        return node;
    }

    @Bean
    public WriteStreamRegistry writeStreamRegistry(){
        return new WriteStreamRegistry();
    }
}
