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

package io.gravitee.am.gateway.handler.common.user;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.User;
import io.gravitee.node.api.cache.CacheException;
import io.gravitee.node.api.cache.ValueMapper;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserValueMapper implements ValueMapper<User, String> {
    private final ObjectMapper objectMapper;

    public UserValueMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String toCachedValue(User value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CacheException("Unable to mapper User object to a String", e);
        }
    }

    @Override
    public User toValue(String cachedValue) {
        try {
            return objectMapper.readValue(cachedValue, User.class);
        } catch (JsonProcessingException e) {
            throw new CacheException("Unable to mapper string to User object", e);
        }
    }
}
