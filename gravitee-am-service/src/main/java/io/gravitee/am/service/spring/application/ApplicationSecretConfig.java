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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.service.exception.ApplicationSecretConfigurationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Objects.isNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class ApplicationSecretConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SecretHashAlgorithm algorithm;

    private Map<String, Object> properties = new TreeMap<>();

    public ApplicationSecretConfig(@Value("${applications.secret.algorithm:None}") String algorithm,
                                   ConfigurableEnvironment environment) {
        this.algorithm = SecretHashAlgorithm.fromAlgorithmName(algorithm);
        for (String property : this.algorithm.getParameters()) {
            var defaultValue = this.algorithm.getDefaultValue(property);
            Object customValue = environment.getProperty("applications.secret.properties." + property, this.algorithm.getTypeValue(property));
            properties.put(property, isNull(customValue) ? defaultValue : customValue);
        }
    }

    public SecretHashAlgorithm getAlgorithm() {
        return algorithm;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public ApplicationSecretSettings toSecretSettings() {
        try {
            final var serializedConfig = MAPPER.writeValueAsString(List.of(this.algorithm, this.properties));
            final var id = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(serializedConfig.getBytes()));
            return new ApplicationSecretSettings(id, this.algorithm.name(), this.properties);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new ApplicationSecretConfigurationException(e);
        }
    }

    public static ApplicationSecretSettings buildNoneSecretSettings() {
        try {
            SecretHashAlgorithm noneAlg = SecretHashAlgorithm.NONE;
            Map<String, Object> noProperties = Map.of();
            final var serializedConfig = MAPPER.writeValueAsString(List.of(noneAlg, noProperties));
            final var id = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(serializedConfig.getBytes()));
            return new ApplicationSecretSettings(id, noneAlg.name(), noProperties);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new ApplicationSecretConfigurationException(e);
        }
    }

}
