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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserTest {

    @Test
    public void should_LastIdentity_AdditionalInfo_Return_ProfileAdditionInfo_noLastIdentityUsed() {
        var user = new User();
        user.setLastIdentityUsed(null);
        Map<String, Object> additionalInfo = Map.of("profile", "value");
        user.setAdditionalInformation(additionalInfo);

        assertThat(user.getLastIdentityInformation()).containsExactlyEntriesOf(additionalInfo);
    }

    @Test
    public void should_LastIdentity_AdditionalInfo_Return_ProfileAdditionInfo_noIdentityEntry() {
        var user = new User();
        user.setLastIdentityUsed(UUID.randomUUID().toString());
        Map<String, Object> additionalInfo = Map.of("profile", "value");
        user.setAdditionalInformation(additionalInfo);

        assertThat(user.getLastIdentityInformation()).containsExactlyEntriesOf(additionalInfo);
    }

    @Test
    public void should_LastIdentity_AdditionalInfo_Return_ProfileAdditionInfo_IdentityEntry_notFound() {
        var user = new User();
        user.setLastIdentityUsed(UUID.randomUUID().toString());
        Map<String, Object> additionalInfo = Map.of("profile", "value");
        user.setAdditionalInformation(additionalInfo);
        UserIdentity identity = new UserIdentity();
        identity.setProviderId(UUID.randomUUID().toString());
        Map<String, Object> identityInfo = Map.of("identity", "value");
        identity.setAdditionalInformation(identityInfo);
        user.setIdentities(List.of(identity));

        assertThat(user.getLastIdentityInformation()).containsExactlyEntriesOf(additionalInfo);
    }

    @Test
    public void should_LastIdentity_AdditionalInfo_Return_IdentityAdditionInfo() {
        var user = new User();
        user.setLastIdentityUsed(UUID.randomUUID().toString());
        Map<String, Object> additionalInfo = Map.of("profile", "value");
        user.setAdditionalInformation(additionalInfo);
        UserIdentity identity = new UserIdentity();
        identity.setProviderId(user.getLastIdentityUsed());
        Map<String, Object> identityInfo = Map.of("identity", "value");
        identity.setAdditionalInformation(identityInfo);
        user.setIdentities(List.of(identity));

        assertThat(user.getLastIdentityInformation()).containsExactlyEntriesOf(identityInfo);
    }

    @Test
    public void should_not_have_identitiesMap() {
        var user = new User();
        user.setLastIdentityUsed(UUID.randomUUID().toString());
        Map<String, Object> additionalInfo = Map.of("profile", "value");
        user.setAdditionalInformation(additionalInfo);

        assertThat(user.getIdentitiesAsMap()).isEmpty();
    }

    @Test
    public void should_provide_identitiesMap() {
        var user = new User();
        user.setLastIdentityUsed(UUID.randomUUID().toString());
        Map<String, Object> additionalInfo = Map.of("profile", "value");
        user.setAdditionalInformation(additionalInfo);
        UserIdentity identity = new UserIdentity();
        identity.setProviderId(user.getLastIdentityUsed());
        Map<String, Object> identityInfo = Map.of("identity", "value");
        identity.setAdditionalInformation(identityInfo);
        UserIdentity identity2 = new UserIdentity();
        identity2.setProviderId(UUID.randomUUID().toString());
        Map<String, Object> identityInfo2 = Map.of("identity2", "value");
        identity2.setAdditionalInformation(identityInfo2);

        user.setIdentities(List.of(identity, identity2));

        assertThat(user.getIdentitiesAsMap())
                .hasSize(2)
                .containsKeys(identity.getProviderId(), identity2.getProviderId());

        assertThat(user.getIdentitiesAsMap().get(identity.getProviderId()))
                .isNotNull()
                .hasFieldOrPropertyWithValue("additionalInformation", identityInfo)
                .hasFieldOrPropertyWithValue("providerId", identity.getProviderId());

        assertThat(user.getIdentitiesAsMap().get(identity2.getProviderId()))
                .isNotNull()
                .hasFieldOrPropertyWithValue("additionalInformation", identityInfo2)
                .hasFieldOrPropertyWithValue("providerId", identity2.getProviderId());
    }

    @Test
    public void shouldNotUpdateSensitivePropertyToPlaceholder() {
        var user = new User();
        final String theSensitiveProperty = ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY;
        user.putAdditionalInformation(theSensitiveProperty, "sensitive value");

        user.setAdditionalInformation(Map.of(theSensitiveProperty, User.SENSITIVE_PROPERTY_PLACEHOLDER));

        assertThat(user.getAdditionalInformation().get(theSensitiveProperty)).isEqualTo("sensitive value");
    }

    @Test
    public void shouldUpdateSensitiveProperty() {
        var user = new User();
        final String theSensitiveProperty = ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY;
        user.putAdditionalInformation(theSensitiveProperty, "original value");

        user.setAdditionalInformation(Map.of(theSensitiveProperty, "updated value"));

        assertThat(user.getAdditionalInformation().get(theSensitiveProperty)).isEqualTo("updated value");
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

        Map<String, EnrolledFactor> map = user.enrolledFactors();
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

        Map<String, EnrolledFactor> map = user.enrolledFactorsByType();
        Assertions.assertNotNull(map.get("SMS"));
        Assertions.assertNotNull(map.get("OTHER"));
        Assertions.assertNotNull(map.get("CALL"));
        Assertions.assertNull(map.get("HTTP"));
    }

    private static Stream<Arguments> stringClaimGetters() {
        return Stream.of(
                Arguments.of(StandardClaims.EMAIL, (Function<User, String>) User::getEmail),
                Arguments.of(StandardClaims.NAME, (Function<User, String>) User::getDisplayName),
                Arguments.of(StandardClaims.GIVEN_NAME, (Function<User, String>) User::getFirstName),
                Arguments.of(StandardClaims.MIDDLE_NAME, (Function<User, String>) User::getMiddleName),
                Arguments.of(StandardClaims.FAMILY_NAME, (Function<User, String>) User::getLastName),
                Arguments.of(StandardClaims.PICTURE, (Function<User, String>) User::getPicture),
                Arguments.of(StandardClaims.PROFILE, (Function<User, String>) User::getProfile),
                Arguments.of(StandardClaims.WEBSITE, (Function<User, String>) User::getWebsite),
                Arguments.of(StandardClaims.BIRTHDATE, (Function<User, String>) User::getBirthdate),
                Arguments.of(StandardClaims.ZONEINFO, (Function<User, String>) User::getZoneInfo),
                Arguments.of(StandardClaims.LOCALE, (Function<User, String>) User::getLocale),
                Arguments.of(StandardClaims.PHONE_NUMBER, (Function<User, String>) User::getPhoneNumber)
        );
    }

    @ParameterizedTest
    @MethodSource("stringClaimGetters")
    public void shouldReturnStringClaim(String claim, Function<User, String> getter) {
        var user = new User();
        user.putAdditionalInformation(claim, "value");

        assertThat(getter.apply(user)).isEqualTo("value");
    }

    @ParameterizedTest
    @MethodSource("stringClaimGetters")
    public void shouldConvertNumericClaimToString(String claim, Function<User, String> getter) {
        var user = new User();
        user.putAdditionalInformation(claim, 12345);

        assertThat(getter.apply(user)).isEqualTo("12345");
    }

    @ParameterizedTest
    @MethodSource("stringClaimGetters")
    public void shouldConvertBooleanClaimToString(String claim, Function<User, String> getter) {
        var user = new User();
        user.putAdditionalInformation(claim, true);

        assertThat(getter.apply(user)).isEqualTo("true");
    }

    @ParameterizedTest
    @MethodSource("stringClaimGetters")
    public void shouldIgnoreStructuredClaim(String claim, Function<User, String> getter) {
        var user = new User();
        user.setUsername("username");
        user.putAdditionalInformation(claim, Map.of("key", "value"));
        var userWithList = new User();
        userWithList.setUsername("username");
        userWithList.putAdditionalInformation(claim, List.of("value"));

        // getDisplayName falls back to the username when no usable claim is found
        String expected = StandardClaims.NAME.equals(claim) ? "username" : null;
        assertThat(getter.apply(user)).isEqualTo(expected);
        assertThat(getter.apply(userWithList)).isEqualTo(expected);
    }

    @Test
    public void shouldIgnoreNonMapAddressClaim() {
        var user = new User();
        user.putAdditionalInformation(StandardClaims.ADDRESS, "street");

        assertThat(user.getAddress()).isNull();
    }

    @Test
    public void shouldSerializeUserWithNonStringStandardClaims() {
        var user = new User();
        stringClaimGetters().forEach(args -> user.putAdditionalInformation((String) args.get()[0], 12345));
        user.putAdditionalInformation(StandardClaims.ADDRESS, 12345);

        assertThatCode(() -> JsonMapper.builder().build().writeValueAsString(user)).doesNotThrowAnyException();
    }

}
