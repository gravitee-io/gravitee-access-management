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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaClientPropertiesBuilderTest {

    private static final String SCHEMA_REGISTRY_URL_KEY = "schema.registry.url";
    private static final String LOGIN_CALLBACK_HANDLER_CLASS_KEY = "sasl.login.callback.handler.class";
    private static final String OLD_OAUTH_CALLBACK_HANDLER_CLASS = "org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerLoginCallbackHandler";
    private static final String OAUTH_CALLBACK_HANDLER_FALLBACK = "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler";
    private static final String OAUTH_ENDPOINT_ALLOWED_LIST = "org.apache.kafka.sasl.oauthbearer.allowed.urls";

    @Mock
    private Predicate<String> classTester;

    private KafkaClientPropertiesBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new KafkaClientPropertiesBuilder(classTester);
        System.clearProperty(OAUTH_ENDPOINT_ALLOWED_LIST);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(OAUTH_ENDPOINT_ALLOWED_LIST);
    }

    private KafkaReporterConfiguration baseConfig() {
        KafkaReporterConfiguration config = new KafkaReporterConfiguration();
        config.setBootstrapServers("localhost:9092");
        config.setAcks("all");
        return config;
    }

    @Test
    void should_set_mandatory_producer_properties() {
        Properties props = builder.getProperties(baseConfig());

        assertThat(props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
        assertThat(props.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
        assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
        verifyNoInteractions(classTester);
    }

    @Test
    void should_use_jackson_serializer_when_no_schema_registry() {
        Properties props = builder.getProperties(baseConfig());

        assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(JacksonSerializer.class);
        assertThat(props.containsKey(SCHEMA_REGISTRY_URL_KEY)).isFalse();
    }

    @Test
    void should_use_kafka_json_serializer_when_schema_registry_present() {
        KafkaReporterConfiguration config = baseConfig();
        config.setSchemaRegistryUrl("http://localhost:8081");

        Properties props = builder.getProperties(config);

        assertThat(props.get(SCHEMA_REGISTRY_URL_KEY)).isEqualTo("http://localhost:8081");
        assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(KafkaJsonSerializer.class);
    }

    @Test
    void should_set_sasl_jaas_config_when_credentials_provided() {
        KafkaReporterConfiguration config = baseConfig();
        config.setUsername("user");
        config.setPassword("secret");

        Properties props = builder.getProperties(config);

        assertThat(props.get(SaslConfigs.SASL_JAAS_CONFIG))
                .isEqualTo("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"user\" password=\"secret\";");
    }

    @Test
    void should_not_set_sasl_jaas_config_when_password_missing() {
        KafkaReporterConfiguration config = baseConfig();
        config.setUsername("user");

        Properties props = builder.getProperties(config);

        assertThat(props.containsKey(SaslConfigs.SASL_JAAS_CONFIG)).isFalse();
    }

    @Test
    void should_pass_through_arbitrary_additional_properties() {
        KafkaReporterConfiguration config = baseConfig();
        config.setAdditionalProperties(List.of(Map.of("option", "compression.type", "value", "gzip")));

        Properties props = builder.getProperties(config);

        assertThat(props.get("compression.type")).isEqualTo("gzip");
        verifyNoInteractions(classTester);
    }

    @Test
    void should_keep_callback_handler_class_when_present_on_classpath() {
        when(classTester.test("com.example.MyHandler")).thenReturn(true);
        KafkaReporterConfiguration config = baseConfig();
        config.setAdditionalProperties(List.of(Map.of("option", LOGIN_CALLBACK_HANDLER_CLASS_KEY, "value", "com.example.MyHandler")));

        Properties props = builder.getProperties(config);

        assertThat(props.get(LOGIN_CALLBACK_HANDLER_CLASS_KEY)).isEqualTo("com.example.MyHandler");
    }

    @Test
    void should_fallback_to_new_callback_handler_when_old_one_missing() {
        when(classTester.test(OLD_OAUTH_CALLBACK_HANDLER_CLASS)).thenReturn(false);
        when(classTester.test(OAUTH_CALLBACK_HANDLER_FALLBACK)).thenReturn(true);
        KafkaReporterConfiguration config = baseConfig();
        config.setAdditionalProperties(List.of(Map.of("option", LOGIN_CALLBACK_HANDLER_CLASS_KEY, "value", OLD_OAUTH_CALLBACK_HANDLER_CLASS)));

        Properties props = builder.getProperties(config);

        assertThat(props.get(LOGIN_CALLBACK_HANDLER_CLASS_KEY)).isEqualTo(OAUTH_CALLBACK_HANDLER_FALLBACK);
    }

    @Test
    void should_throw_when_callback_handler_class_missing_and_no_fallback() {
        when(classTester.test("com.example.Missing")).thenReturn(false);
        KafkaReporterConfiguration config = baseConfig();
        config.setAdditionalProperties(List.of(Map.of("option", LOGIN_CALLBACK_HANDLER_CLASS_KEY, "value", "com.example.Missing")));

        assertThatThrownBy(() -> builder.getProperties(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("com.example.Missing");
    }

    @Test
    void should_throw_when_old_callback_handler_missing_and_fallback_also_missing() {
        when(classTester.test(OLD_OAUTH_CALLBACK_HANDLER_CLASS)).thenReturn(false);
        when(classTester.test(OAUTH_CALLBACK_HANDLER_FALLBACK)).thenReturn(false);
        KafkaReporterConfiguration config = baseConfig();
        config.setAdditionalProperties(List.of(Map.of("option", LOGIN_CALLBACK_HANDLER_CLASS_KEY, "value", OLD_OAUTH_CALLBACK_HANDLER_CLASS)));

        assertThatThrownBy(() -> builder.getProperties(config))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_set_oauth_allowed_urls_system_property() {
        KafkaReporterConfiguration config = baseConfig();
        config.setAdditionalProperties(List.of(Map.of("option", OAUTH_ENDPOINT_ALLOWED_LIST, "value", "https://idp.example.com/token")));

        Properties props = builder.getProperties(config);

        assertThat(System.getProperty(OAUTH_ENDPOINT_ALLOWED_LIST)).isEqualTo("https://idp.example.com/token");
        // value handled via system property, not added to producer properties
        assertThat(props.containsKey(OAUTH_ENDPOINT_ALLOWED_LIST)).isFalse();
    }

    @Test
    void should_append_oauth_allowed_url_to_existing_system_property() {
        System.setProperty(OAUTH_ENDPOINT_ALLOWED_LIST, "https://existing.example.com/token");
        KafkaReporterConfiguration config = baseConfig();
        config.setAdditionalProperties(List.of(Map.of("option", OAUTH_ENDPOINT_ALLOWED_LIST, "value", "https://idp.example.com/token")));

        builder.getProperties(config);

        assertThat(System.getProperty(OAUTH_ENDPOINT_ALLOWED_LIST))
                .isEqualTo("https://existing.example.com/token,https://idp.example.com/token");
    }

    @Test
    void should_not_duplicate_oauth_allowed_url_when_already_present() {
        System.setProperty(OAUTH_ENDPOINT_ALLOWED_LIST, "https://idp.example.com/token");
        KafkaReporterConfiguration config = baseConfig();
        config.setAdditionalProperties(List.of(Map.of("option", OAUTH_ENDPOINT_ALLOWED_LIST, "value", "https://idp.example.com/token")));

        builder.getProperties(config);

        assertThat(System.getProperty(OAUTH_ENDPOINT_ALLOWED_LIST)).isEqualTo("https://idp.example.com/token");
    }
}
