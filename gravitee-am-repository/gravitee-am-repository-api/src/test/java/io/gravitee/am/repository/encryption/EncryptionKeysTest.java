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
package io.gravitee.am.repository.encryption;

import io.gravitee.am.repository.encryption.FieldEncryptor.EncryptionKey;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionKeysTest {

    private static final String CONFIGURATION = "{\"password\":\"s3cr3t\"}";

    @Test
    void should_read_the_declared_keys() {
        var environment = environment(Map.of(
                "encryptionKeys[0].id", "v1",
                "encryptionKeys[0].secret", "secret-1",
                "encryptionKeys[1].id", "v2",
                "encryptionKeys[1].secret", "secret-2"));

        assertThat(EncryptionKeys.fromEnvironment(environment))
                .containsExactly(new EncryptionKey("v1", "secret-1"), new EncryptionKey("v2", "secret-2"));
    }

    @Test
    void should_encrypt_with_the_selected_key_when_enabled() {
        var environment = environment(Map.of(
                "plugins.properties.configuration.encryption.enabled", "true",
                "plugins.properties.configuration.encryption.key", "v1"));

        assertThat(EncryptionKeys.currentKeyId(environment)).contains("v1");
    }

    @Test
    void should_not_encrypt_when_disabled_even_with_a_selected_key() {
        var environment = environment(Map.of(
                "plugins.properties.configuration.encryption.enabled", "false",
                "plugins.properties.configuration.encryption.key", "v1"));

        assertThat(EncryptionKeys.currentKeyId(environment)).isEmpty();
    }

    @Test
    void should_not_encrypt_by_default() {
        var environment = environment(Map.of("plugins.properties.configuration.encryption.key", "v1"));

        assertThat(EncryptionKeys.currentKeyId(environment)).isEmpty();
    }

    @Test
    void should_refuse_to_enable_the_encryption_without_a_key() {
        var environment = environment(Map.of("plugins.properties.configuration.encryption.enabled", "true"));

        assertThatThrownBy(() -> EncryptionKeys.currentKeyId(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plugins.properties.configuration.encryption.key");
    }

    @Test
    void should_refuse_to_enable_the_encryption_with_a_key_that_is_not_declared() {
        var environment = environment(Map.of(
                "encryptionKeys[0].id", "v1",
                "encryptionKeys[0].secret", "secret-1",
                "plugins.properties.configuration.encryption.enabled", "true",
                "plugins.properties.configuration.encryption.key", "v2"));

        assertThatThrownBy(() -> EncryptionKeys.encryptor(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[v2]");
    }

    @Test
    void should_decrypt_with_the_declared_keys_only_like_a_gateway() {
        Map<String, Object> managementApi = new HashMap<>(Map.of(
                "encryptionKeys[0].id", "v1",
                "encryptionKeys[0].secret", "secret-1",
                "plugins.properties.configuration.encryption.enabled", "true",
                "plugins.properties.configuration.encryption.key", "v1"));
        String encrypted = EncryptionKeys.encryptor(environment(managementApi)).encrypt(CONFIGURATION);
        // a Gateway declares the keys, without any plugins section
        var gateway = environment(Map.of(
                "encryptionKeys[0].id", "v1",
                "encryptionKeys[0].secret", "secret-1"));

        FieldEncryptor gatewayEncryptor = EncryptionKeys.encryptor(gateway);

        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(gatewayEncryptor.decrypt(encrypted)).isEqualTo(CONFIGURATION);
    }

    private static StandardEnvironment environment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }
}
