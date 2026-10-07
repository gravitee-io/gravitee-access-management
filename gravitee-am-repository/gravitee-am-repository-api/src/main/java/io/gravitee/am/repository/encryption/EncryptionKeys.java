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

import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.encryption.FieldEncryptor.EncryptionKey;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the keys encrypting repository fields from {@value #KEYS_PROPERTY}{@code [i].id} and
 * {@code [i].secret}, oldest first: the last one encrypts, the others only decrypt the values they encrypted.
 *
 * @author GraviteeSource Team
 */
public final class EncryptionKeys {

    public static final String KEYS_PROPERTY = Scope.MANAGEMENT.getRepositoryPropertyKey() + ".encryption.keys";

    private EncryptionKeys() {
    }

    /**
     * @return the configured keys, oldest first, or an empty list when encryption is disabled
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
     * @return the id of the key that encrypts, or empty when encryption is disabled
     */
    public static Optional<String> currentKeyId(Environment environment) {
        List<EncryptionKey> keys = fromEnvironment(environment);
        return keys.isEmpty() ? Optional.empty() : Optional.of(keys.getLast().id());
    }
}
