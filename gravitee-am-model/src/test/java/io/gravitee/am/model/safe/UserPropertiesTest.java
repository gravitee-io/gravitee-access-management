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
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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

    @Test
    public void shouldReturnEnrolledFactorsAsMap() {
        var user = new User();

        user.setFactors(new ArrayList<>());

        EnrolledFactor sms = new EnrolledFactor();
        sms.setFactorId("id1");
        EnrolledFactorChannel smsChannel = new EnrolledFactorChannel();
        smsChannel.setType(EnrolledFactorChannel.Type.SMS);
        smsChannel.setTarget("123123123");
        sms.setChannel(smsChannel);

        EnrolledFactor call = new EnrolledFactor();
        call.setFactorId("id2");
        EnrolledFactorChannel callChannel = new EnrolledFactorChannel();
        callChannel.setType(EnrolledFactorChannel.Type.CALL);
        callChannel.setTarget("123123123");
        call.setChannel(callChannel);

        EnrolledFactor other1 = new EnrolledFactor();
        other1.setFactorId("other1");

        EnrolledFactor other2 = new EnrolledFactor();
        other2.setFactorId("other2");

        user.getFactors().add(sms);
        user.getFactors().add(call);
        user.getFactors().add(other1);
        user.getFactors().add(other2);

        Map<String, EnrolledFactorProperties> map = new UserProperties(user, true).enrolledFactors();
        Assertions.assertNotNull(map.get("id1"));
        Assertions.assertNotNull(map.get("id2"));
        Assertions.assertNotNull(map.get("other1"));
        Assertions.assertNotNull(map.get("other2"));

    }

    @Test
    public void shouldReturnEnrolledFactorsByTypeWithUniqueKeys() {
        var user = new User();

        user.setFactors(new ArrayList<>());

        EnrolledFactor sms = new EnrolledFactor();
        sms.setFactorId("sms");
        EnrolledFactorChannel smsChannel = new EnrolledFactorChannel();
        smsChannel.setType(EnrolledFactorChannel.Type.SMS);
        smsChannel.setTarget("123123123");
        sms.setChannel(smsChannel);

        EnrolledFactor call = new EnrolledFactor();
        call.setFactorId("call");
        EnrolledFactorChannel callChannel = new EnrolledFactorChannel();
        callChannel.setType(EnrolledFactorChannel.Type.CALL);
        callChannel.setTarget("123123123");
        call.setChannel(callChannel);

        EnrolledFactor call2 = new EnrolledFactor();
        call2.setFactorId("call");
        call2.setChannel(callChannel);

        EnrolledFactor other1 = new EnrolledFactor();
        other1.setFactorId("other1");

        EnrolledFactor other2 = new EnrolledFactor();
        other2.setFactorId("other2");

        user.getFactors().add(sms);
        user.getFactors().add(call);
        user.getFactors().add(call2);
        user.getFactors().add(other1);
        user.getFactors().add(other2);

        Map<String, EnrolledFactorProperties> map = new UserProperties(user, true).enrolledFactorsByType();
        Assertions.assertNotNull(map.get("SMS"));
        Assertions.assertNotNull(map.get("OTHER"));
        Assertions.assertNotNull(map.get("CALL"));
        Assertions.assertNull(map.get("HTTP"));
    }

}