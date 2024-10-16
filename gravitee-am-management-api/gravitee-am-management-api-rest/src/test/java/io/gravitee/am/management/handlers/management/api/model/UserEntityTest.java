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
package io.gravitee.am.management.handlers.management.api.model;

import io.gravitee.am.model.User;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


public class UserEntityTest {
    @Test
    public void shouldHideSensitiveInfo() {
        User user = new User();
        user.setUsername("username");
        user.setPassword("password");
        user.setEmail("email");
        user.setFirstName("firstName");
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("key1", "value1");
        additionalInfo.put("key2", null);
        additionalInfo.put("key3", "value3");
        additionalInfo.put("op_id_token", "token");
        user.setAdditionalInformation(additionalInfo);
        UserEntity userEntity = new UserEntity(user);
        assertEquals(4, userEntity.getAdditionalInformation().size());
        assertNotEquals("token", userEntity.getAdditionalInformation().get("op_id_token"));
        assertNull(userEntity.getAdditionalInformation().get("key2"));
    }
}