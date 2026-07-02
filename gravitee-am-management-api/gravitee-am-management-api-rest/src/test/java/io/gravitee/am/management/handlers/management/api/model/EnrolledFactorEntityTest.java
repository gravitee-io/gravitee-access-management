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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class EnrolledFactorEntityTest {

    @Test
    public void shouldExposeTargetButHideSecurityValues() throws Exception {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factor-id");

        EnrolledFactorSecurity security = new EnrolledFactorSecurity();
        security.setType("SHARED_SECRET");
        security.setValue("super-secret-otp-key");
        Map<String, Object> securityAdditionalData = new HashMap<>();
        securityAdditionalData.put("secretKey", "another-secret-value");
        security.setAdditionalData(securityAdditionalData);
        enrolledFactor.setSecurity(security);

        EnrolledFactorChannel channel = new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, "+33612345678");
        Map<String, Object> channelAdditionalData = new HashMap<>();
        channelAdditionalData.put("token", "channel-secret-token");
        channel.setAdditionalData(channelAdditionalData);
        enrolledFactor.setChannel(channel);

        EnrolledFactorEntity entity = new EnrolledFactorEntity(enrolledFactor);

        // relevant information is exposed
        assertEquals("factor-id", entity.getId());
        assertEquals("+33612345678", entity.getTarget());

        // sensitive data is not exposed, even via serialization
        String json = new ObjectMapper().writeValueAsString(entity);
        assertFalse(json.contains("super-secret-otp-key"));
        assertFalse(json.contains("another-secret-value"));
        assertFalse(json.contains("channel-secret-token"));
        assertFalse(json.toLowerCase().contains("security"));
    }
}
