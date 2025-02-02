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
package io.gravitee.am.repository.jdbc.provider.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.OCTKey;
import io.gravitee.am.model.jose.OKPKey;
import io.gravitee.am.model.jose.RSAKey;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JSONMapper {

    private static ObjectMapper mapper;

    static {
        mapper =  new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.addMixIn(JWK.class, JWKMixIn.class);
    }

    public static <T> T toBean(String json, Class<T> beanClass)  {
        T result = null;
        if (json != null) {
            try {
                result = mapper.readValue(json, beanClass);
            } catch (JsonProcessingException e) {
                throw new JsonMapperException("Unable to instantiate Bean " + beanClass.getName(), e);
            }
        }
        return result;
    }

    public static <T> T toCollectionOfBean(String json, TypeReference typeRef)  {
        T result = null;
        if (json != null) {
            try {
                result = (T) mapper.readValue(json, typeRef);
            } catch (JsonProcessingException e) {
                throw new JsonMapperException("Unable to instantiate Bean " + typeRef.getType(), e);
            }
        }
        return result;
    }


    public static String toJson(Object object) {
        String result = null;
        if (object != null) {
            try {
                result = mapper.writeValueAsString(object);
            } catch (JsonProcessingException e) {
                throw new JsonMapperException("Unable to serialize Bean " + object.getClass().getName(), e);
            }
        }
        return result;
    }

    public static String secretSettingsToJson(List<ApplicationSecretSettings> secretSettingsList) {
        String result = null;
        if (secretSettingsList != null) {
            try {
                result = mapper.writeValueAsString(secretSettingsList.stream().map(JdbcApplicationSecretSettings::new).collect(Collectors.toList()));
            } catch (JsonProcessingException e) {
                throw new JsonMapperException("Unable to serialize Bean " + secretSettingsList.getClass().getName(), e);
            }
        }
        return result;
    }

    public static class JsonMapperException extends RuntimeException {
        public JsonMapperException(String message, Throwable cause) {
            super(message, cause);
        }
    }


    public static class JdbcApplicationSecretSettings {

        private String id;

        private String algorithm;

        private Map<String, Object> properties = new TreeMap<>();

        public JdbcApplicationSecretSettings() {
        }

        public JdbcApplicationSecretSettings(ApplicationSecretSettings settings) {
            this.id = settings.getId();
            this.algorithm = settings.getAlgorithm();
            this.properties = settings.getProperties() != null ? new TreeMap<>(settings.getProperties()) : new TreeMap<>();
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }
    }

    /**
     * @author Eric LELEU (eric.leleu at graviteesource.com)
     * @author GraviteeSource Team
     */

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "kty")
    @JsonSubTypes({
            // OCTKey has a lower case value.
            @JsonSubTypes.Type(value = OCTKey.class, name = "oct"),
            @JsonSubTypes.Type(value = OKPKey.class, name = "OKP"),
            @JsonSubTypes.Type(value = RSAKey.class, name = "RSA"),
            @JsonSubTypes.Type(value = ECKey.class, name = "EC")
    })
    /**
     * This Mixin allows to manage the JWK class hierarchy in order to Serialize/deserialize JWK
     * entry into the Application.settings.oauth configuration
     */
    public static class JWKMixIn {
    }
}
