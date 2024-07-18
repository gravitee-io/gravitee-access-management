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
package io.gravitee.am.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class RememberDeviceSettingsTest {


    public static TestCase[] examples() {
        return new TestCase[]{
                // null simulate field being unset in the incoming JSON payload
                settings(null, null, null, null),

                settings(false, null, null, null),
                settings(true, null, null, null),

                settings(null, false, null, null),
                settings(null, true, null, null),

                settings(null, null, 123456L, null),

                settings(null, null, 123456L, "test-device-identifier"),

                settings(true, true, 123456L, "test-device-identifier"),
        };
    }

    @ParameterizedTest
    @MethodSource("examples")
    void copyConstructorCopiesProperly(TestCase testCase) {
        var source = testCase.expectedSettings();
        assertThat(new RememberDeviceSettings(source)).isEqualTo(source);
    }

    // github: #9734
    @ParameterizedTest
    @MethodSource("examples")
    void jsonMapper_readsCorrectly(TestCase testCase) throws Exception {
        var expectedSettings = testCase.expectedSettings;
        var read = getMapper().treeToValue(testCase.json(), RememberDeviceSettings.class);
        assertThat(read)
                .as("json is read correctly")
                .usingRecursiveComparison()
                .isEqualTo(expectedSettings);

    }

    @ParameterizedTest
    @MethodSource("examples")
    void jsonMapperSerialization_roundTrips(TestCase testCase) throws Exception {
        var mapper = getMapper();
        var expectedSettings = testCase.expectedSettings;
        var readBack = mapper.readValue(mapper.writeValueAsBytes(expectedSettings), RememberDeviceSettings.class);
        assertThat(readBack)
                .as("serialization round-trips")
                .usingRecursiveComparison()
                .isEqualTo(expectedSettings);
    }

    private static JsonMapper getMapper() {
        var mapper = new JsonMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    static TestCase settings(Boolean active,
                                           Boolean skipRememberDevice,
                                           Long expirationTimeSeconds,
                                           String deviceIdentifierId) {
        var json = getMapper().createObjectNode();
        var expectedSettings = new RememberDeviceSettings();

        if (active != null) {
            expectedSettings.setActive(active);
            json.put("active", active);
        }
        if (skipRememberDevice != null) {
            expectedSettings.setSkipChallengeWhenRememberDevice(skipRememberDevice);
            json.put("skipRememberDevice", skipRememberDevice);
        }
        expectedSettings.setExpirationTimeSeconds(expirationTimeSeconds);
        json.put("expirationTimeSeconds", expirationTimeSeconds);

        expectedSettings.setDeviceIdentifierId(deviceIdentifierId);
        json.put("deviceIdentifierId", deviceIdentifierId);
        return new TestCase(json, expectedSettings);
    }

    record TestCase(ObjectNode json, RememberDeviceSettings expectedSettings) {}

}
