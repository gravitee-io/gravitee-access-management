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
package io.gravitee.am.management.service.impl.notifications;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Map;

import static java.lang.String.format;

@Configuration
public class KafkaNotifierConfigurationProvider {

    @Bean
    public KafkaNotifierConfiguration kafkaNotifierConfiguration(io.gravitee.node.api.configuration.Configuration configuration) {

        final var additionalProperties = new ArrayList<Map<String, String>>();
        int propertyIndex = 0;
        while(configuration.getProperty(format("notifiers.kafka.additionalProperties[%d].name", propertyIndex)) != null) {
            final var name = configuration.getProperty(format("notifiers.kafka.additionalProperties[%d].name", propertyIndex), String.class);
            final var value = configuration.getProperty(format("notifiers.kafka.additionalProperties[%d].value", propertyIndex), String.class);
            additionalProperties.add(Map.of(name, value));
            ++propertyIndex;
        }

        return KafkaNotifierConfiguration.builder()
                .bootstrapServers(configuration.getProperty("notifiers.kafka.bootstrapServers"))
                .topic(configuration.getProperty("notifiers.kafka.topic"))
                .acks(configuration.getProperty("notifiers.kafka.acks"))
                .username(configuration.getProperty("notifiers.kafka.username"))
                .password(configuration.getProperty("notifiers.kafka.password"))
                .schemaRegistryUrl(configuration.getProperty("notifiers.kafka.schemaRegistryUrl"))
                .additionalProperties(additionalProperties)
                .build();
    }

}
