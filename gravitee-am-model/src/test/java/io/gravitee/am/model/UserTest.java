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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

}
