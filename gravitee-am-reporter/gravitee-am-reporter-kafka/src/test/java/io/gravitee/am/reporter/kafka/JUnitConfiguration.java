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
import io.gravitee.node.api.Node;
import io.vertx.core.Vertx;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Florent Amaridon
 * @author Visiativ
 */
@Configuration
@ComponentScan("io.gravitee.am.reporter.kafka")
public class JUnitConfiguration implements ApplicationListener<ContextClosedEvent> {

    private KafkaContainer kafkaContainer;

    @Bean
    public KafkaContainer kafkaContainer(){
        this.kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:6.1.1"));
        kafkaContainer.start();
        return kafkaContainer;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent contextClosedEvent) {
        if(this.kafkaContainer != null){
            this.kafkaContainer.stop();
        }
    }

    @Bean
    public KafkaReporterConfiguration getTestConfiguration(KafkaContainer kafkaContainer) {
        KafkaReporterConfiguration kafkaReporterConfiguration = new KafkaReporterConfiguration();
        kafkaReporterConfiguration.setTopic("gravitee-audit");
        kafkaReporterConfiguration.setBootstrapServers(kafkaContainer.getBootstrapServers());
        kafkaReporterConfiguration.setAcks("1");
        return kafkaReporterConfiguration;
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
        return new DummyNode("node-id", "main.srv.local");
    }
}
