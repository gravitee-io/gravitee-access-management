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
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the encryption settings of the plugin configurations from gravitee.yml:
 * <ul>
 *     <li>{@value #KEYS_PROPERTY}{@code [i].id} and {@code [i].secret} declare the keys; each one decrypts
 *     the values it encrypted;</li>
 *     <li>{@value #ENABLED_PROPERTY} turns the encryption on, with the key named by {@value #CURRENT_KEY_PROPERTY}.
 *     When it is off, configurations are stored in clear text and the selected key can stay in place.</li>
 * </ul>
 * The declared keys decrypt whatever the encryption flag is, so a node that only reads configurations,
 * like the Gateway, needs nothing but the keys.
 *
 * @author GraviteeSource Team
 */
public final class EncryptionKeys {

    public static final String KEYS_PROPERTY = "encryptionKeys";
    public static final String ENABLED_PROPERTY = "plugins.properties.configuration.encryption.enabled";
    public static final String CURRENT_KEY_PROPERTY = "plugins.properties.configuration.encryption.key";

    private EncryptionKeys() {
    }

    /**
     * @return the declared keys, possibly none
     */
    public static List<EncryptionKey> fromEnvironment(Environment environment) {
        List<EncryptionKey> keys = new ArrayList<>();
        for (int i = 0; environment.containsProperty(KEYS_PROPERTY + "[" + i + "].id"); i++) {
            String prefix = KEYS_PROPERTY + "[" + i + "].";
            keys.add(new EncryptionKey(environment.getProperty(prefix + "id"), environment.getProperty(prefix + "secret")));
        }
        return keys;
    }

    /**
     * @return the id of the key that encrypts, or empty when the encryption is disabled
     * @throws IllegalArgumentException when the encryption is enabled without a key
     */
    public static Optional<String> currentKeyId(Environment environment) {
        if (!environment.getProperty(ENABLED_PROPERTY, Boolean.class, false)) {
            return Optional.empty();
        }
        String keyId = environment.getProperty(CURRENT_KEY_PROPERTY);
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("Encryption is enabled with " + ENABLED_PROPERTY + " but no key is selected with " + CURRENT_KEY_PROPERTY);
        }
        return Optional.of(keyId);
    }

    /**
     * @throws IllegalArgumentException when a key is invalid, or when the encryption is enabled without a declared key
     */
    public static FieldEncryptor encryptor(Environment environment) {
        return FieldEncryptor.withKeys(fromEnvironment(environment), currentKeyId(environment).orElse(null));
    }
}
