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

package io.gravitee.am.service.spring.application;

import java.util.Map;
import java.util.Set;

public enum SecretHashAlgorithm {
    BCRYPT("BCrypt", Map.of(PropertyKeys.BCRYPT_ROUNDS.key, PropertyKeys.BCRYPT_ROUNDS.value)),
    SHA_512("SHA-512", Map.of()),
    SHA_256("SHA-256", Map.of()),
    PBKDF2("PBKDF2", Map.of(PropertyKeys.PBKDF2_ROUNDS.key, PropertyKeys.PBKDF2_ROUNDS.value,
            PropertyKeys.PBKDF2_SALT.key, PropertyKeys.PBKDF2_SALT.value,
            PropertyKeys.PBKDF2_KEY_ALG.key, PropertyKeys.PBKDF2_KEY_ALG.value)),
    NONE("None", Map.of());

    private final String algorithm;

    private final Map<String, Object> parameters;

    SecretHashAlgorithm(String algorithm, Map<String, Object> params) {
        this.algorithm = algorithm;
        this.parameters = params;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public static SecretHashAlgorithm fromAlgorithmName(String name) {
        for (SecretHashAlgorithm algo : values()) {
            if (algo.algorithm.equalsIgnoreCase(name)) {
                return algo;
            }
        }
        throw new IllegalArgumentException("Algorithm " + name + " not supported");
    }

    public Set<String> getParameters() {
        return parameters.keySet();
    }

    public Object getDefaultValue(String param) {
        return this.parameters.get(param);
    }

    public Class<?> getTypeValue(String key) {
        return PropertyKeys.fromKey(key).getType();
    }

    public enum PropertyKeys {

        BCRYPT_ROUNDS("rounds", 10, Integer.class),
        PBKDF2_ROUNDS("rounds", 600000, Integer.class),
        PBKDF2_SALT("salt", 16, Integer.class),
        PBKDF2_KEY_ALG("algorithm", "PBKDF2WithHmacSHA256", String.class);

        private final String key;
        private final Object value;
        private final Class<?> type;

        PropertyKeys(String key, Object value, Class<?> type) {
            this.key = key;
            this.value = value;
            this.type = type;
        }

        public String getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public Class<?> getType() {
            return type;
        }
        public static PropertyKeys fromKey(String key) {
            for (PropertyKeys prop : values()) {
                if (prop.key.equals(key)) {
                    return prop;
                }
            }
            throw new IllegalArgumentException("PropertyKeys " + key + " not supported");
        }
    }
}
