
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