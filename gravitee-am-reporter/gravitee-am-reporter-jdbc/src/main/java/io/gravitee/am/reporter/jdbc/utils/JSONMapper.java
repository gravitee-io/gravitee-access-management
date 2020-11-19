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
package io.gravitee.am.reporter.jdbc.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JSONMapper {

    private static ObjectMapper mapper;

    static {
        mapper =  new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
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

    public static class JsonMapperException extends RuntimeException {
        public JsonMapperException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
