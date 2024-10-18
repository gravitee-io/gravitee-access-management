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
package io.gravitee.am.model.safe;

import io.gravitee.am.model.User;
import io.gravitee.am.model.UserIdentity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class UserPropertiesTest {

    @Test
    public void shouldRemoveSensitiveInformation(){
        User user = new User();
        user.setUsername("username");
        user.setPassword("password");
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("op_access_token", "value1");
        additionalInfo.put("op_id_token", "value2");
        user.setAdditionalInformation(additionalInfo);

        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setAdditionalInformation(additionalInfo);
        user.setIdentities(List.of(userIdentity));
        UserProperties userProperties = new UserProperties(user, false);
        Assertions.assertTrue(userProperties.getAdditionalInformation().isEmpty());
        Assertions.assertTrue(userProperties.getIdentities().get(0).getAdditionalInformation().isEmpty());
    }

    @Test
    public void shouldKeepSensitiveInformation(){
        User user = new User();
        user.setUsername("username");
        user.setPassword("password");
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("op_access_token", "value1");
        additionalInfo.put("op_id_token", "value2");
        user.setAdditionalInformation(additionalInfo);
        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setAdditionalInformation(additionalInfo);
        user.setIdentities(List.of(userIdentity));
        UserProperties userProperties = new UserProperties(user, true);
        Assertions.assertEquals("value1", userProperties.getAdditionalInformation().get("op_access_token"));
        Assertions.assertEquals("value2", userProperties.getAdditionalInformation().get("op_id_token"));
        Assertions.assertEquals("value1", userProperties.getIdentities().get(0).getAdditionalInformation().get("op_access_token"));
        Assertions.assertEquals("value2", userProperties.getIdentities().get(0).getAdditionalInformation().get("op_id_token"));
    }

}