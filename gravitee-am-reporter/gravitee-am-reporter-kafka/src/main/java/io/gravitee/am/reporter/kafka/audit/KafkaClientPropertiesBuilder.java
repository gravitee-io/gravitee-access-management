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
package io.gravitee.am.reporter.kafka.audit;

import io.gravitee.am.reporter.kafka.KafkaReporterConfiguration;
import io.gravitee.am.reporter.kafka.kafka.JacksonSerializer;
import io.gravitee.am.reporter.kafka.kafka.KafkaJsonSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

@Slf4j
class KafkaClientPropertiesBuilder {
    private static final String SCHEMA_REGISTRY_URL_KEY = "schema.registry.url";
    private static final String SASL_JAAS_CONFIG_PLACEHOLDER = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";";

    private final static String LOGIN_CALLBACK_HANDLER_CLASS_KEY = "sasl.login.callback.handler.class";
    private final static String OLD_OAUTH_CALLBACK_HANDLER_CLASS = "org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerLoginCallbackHandler";
    private final static String OAUTH_CALLBACK_HANDLER_FALLBACK = "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler";

    private final static String OAUTH_ENDPOINT_ALLOWED_LIST = "org.apache.kafka.sasl.oauthbearer.allowed.urls";

    private final static Object OAUTH_ENDPOINT_ALLOWED_LIST_LOCK = new Object();

    private final Predicate<String> classTester;

    KafkaClientPropertiesBuilder(Predicate<String> classTester) {
        this.classTester = classTester;
    }

    KafkaClientPropertiesBuilder() {
        this.classTester = clazzName -> {
            try{
                Class.forName(clazzName);
                return true;
            } catch (ClassNotFoundException e){
                log.warn(e.getMessage(), e);
                return false;
            }
        };
    }

    Properties getProperties(KafkaReporterConfiguration config) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        properties.put(ProducerConfig.ACKS_CONFIG, config.getAcks());
        if (StringUtils.hasText(config.getSchemaRegistryUrl())) {
            properties.put(SCHEMA_REGISTRY_URL_KEY, config.getSchemaRegistryUrl());
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);
        } else {
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonSerializer.class);
        }
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        if (StringUtils.hasText(config.getUsername()) && StringUtils.hasText(config.getPassword())) {
            properties.put(SaslConfigs.SASL_JAAS_CONFIG, String.format(SASL_JAAS_CONFIG_PLACEHOLDER, config.getUsername(), config.getPassword()));
        }

        List<Map<String, String>> additionalProperties = config.getAdditionalProperties();
        if (additionalProperties != null && !additionalProperties.isEmpty()) {
            additionalProperties.forEach(property -> {
                String option = property.get("option");
                String value = property.get("value");
                switch (option) {
                    case LOGIN_CALLBACK_HANDLER_CLASS_KEY -> properties.put(option, handleMissingOAuthCallbackHandlerClass(value));
                    case OAUTH_ENDPOINT_ALLOWED_LIST -> setupOAuthEndpointAllowedList(value);
                    default -> properties.put(option, value);
                }
            });
        }

        Properties props = new Properties();
        props.putAll(properties);

        return props;
    }

    private void setupOAuthEndpointAllowedList(String value) {
        synchronized (OAUTH_ENDPOINT_ALLOWED_LIST_LOCK) {
            String property = System.getProperty(OAUTH_ENDPOINT_ALLOWED_LIST);
            if(property == null || property.isEmpty()) {
                System.setProperty(OAUTH_ENDPOINT_ALLOWED_LIST, value);
            } else {
                if(!property.contains(value)) {
                    System.setProperty(OAUTH_ENDPOINT_ALLOWED_LIST, String.join(",", property, value));
                }
            }
        }
    }

    private String handleMissingOAuthCallbackHandlerClass(String value) {
        if(classTester.test(value)) {
            return value;
        } else {
            if(value.trim().equals(OLD_OAUTH_CALLBACK_HANDLER_CLASS) && classTester.test(OAUTH_CALLBACK_HANDLER_FALLBACK)){
                return OAUTH_CALLBACK_HANDLER_FALLBACK;
            } else {
                throw new IllegalStateException("Class not found " + value);
            }
        }
    }
}
