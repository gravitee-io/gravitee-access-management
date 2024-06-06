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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.model.scim.Certificate;
import io.gravitee.am.repository.management.api.CommonUserRepository.UpdateActions;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UpdateActionsTest {

    @Test
    public void shouldUpdateProfileOnly() {
        var actions = UpdateActions.none();
        assertFalse(actions.updateRole());
        assertFalse(actions.updateAddresses());
        assertFalse(actions.updateAttributes());
        assertFalse(actions.updateDynamicRole());
        assertFalse(actions.updateEntitlements());
    }

    @Test
    public void shouldUpdateAll() {
        var actions = UpdateActions.updateAll();
        assertTrue(actions.updateRole());
        assertTrue(actions.updateAddresses());
        assertTrue(actions.updateAttributes());
        assertTrue(actions.updateDynamicRole());
        assertTrue(actions.updateEntitlements());
    }

    @Test
    public void shouldUpdate_OnlyProfile() {
        var user = buildUser(UUID.randomUUID().toString());
        var actions = UpdateActions.build(user, user);

        assertFalse(actions.updateRequire());
        assertFalse(actions.updateRole());
        assertFalse(actions.updateAddresses());
        assertFalse(actions.updateAttributes());
        assertFalse(actions.updateDynamicRole());
        assertFalse(actions.updateEntitlements());
    }

    @Test
    public void shouldUpdate_Profile_emptyRole() {
        final String uuid = UUID.randomUUID().toString();
        var user = buildUser(uuid);
        user.setRoles(null);
        var user2 = buildUser(uuid);
        user2.setRoles(Collections.emptyList());

        var actions = UpdateActions.build(user, user2);

        assertFalse(actions.updateRole());
        assertFalse(actions.updateRequire());
        assertFalse(actions.updateAddresses());
        assertFalse(actions.updateAttributes());
        assertFalse(actions.updateDynamicRole());
        assertFalse(actions.updateEntitlements());
    }

    @Test
    public void shouldUpdate_ProfileAndRole() {
        final String uuid = UUID.randomUUID().toString();
        var user = buildUser(uuid);
        var user2 = buildUser(uuid);
        user2.setRoles(Arrays.asList(UUID.randomUUID().toString()));

        var actions = UpdateActions.build(user, user2);
        assertTrue(actions.updateRole());
        assertTrue(actions.updateRequire());

        assertFalse(actions.updateAddresses());
        assertFalse(actions.updateAttributes());
        assertFalse(actions.updateDynamicRole());
        assertFalse(actions.updateEntitlements());
    }

    @Test
    public void shouldUpdate_ProfileAndDynRole() {
        final String uuid = UUID.randomUUID().toString();
        var user = buildUser(uuid);
        var user2 = buildUser(uuid);
        user2.setDynamicRoles(Arrays.asList(UUID.randomUUID().toString()));

        var actions = UpdateActions.build(user, user2);
        assertTrue(actions.updateDynamicRole());
        assertTrue(actions.updateRequire());

        assertFalse(actions.updateRole());
        assertFalse(actions.updateAddresses());
        assertFalse(actions.updateAttributes());
        assertFalse(actions.updateEntitlements());
    }

    @Test
    public void shouldUpdate_ProfileAndEntitlements() {
        final String uuid = UUID.randomUUID().toString();
        var user = buildUser(uuid);
        var user2 = buildUser(uuid);
        user2.setEntitlements(Arrays.asList(UUID.randomUUID().toString()));

        var actions = UpdateActions.build(user, user2);
        assertTrue(actions.updateEntitlements());
        assertTrue(actions.updateRequire());

        assertFalse(actions.updateRole());
        assertFalse(actions.updateAddresses());
        assertFalse(actions.updateAttributes());
        assertFalse(actions.updateDynamicRole());
    }

    @Test
    public void shouldUpdate_ProfileAndAddresses() {
        final String uuid = UUID.randomUUID().toString();
        var user = buildUser(uuid);
        var user2 = buildUser(uuid);
        Address addr = new Address();
        addr.setCountry(UUID.randomUUID().toString());
        user2.setAddresses(Arrays.asList(addr));

        var actions = UpdateActions.build(user, user2);
        assertTrue(actions.updateRequire());
        assertTrue(actions.updateAddresses());

        assertFalse(actions.updateRole());
        assertFalse(actions.updateAttributes());
        assertFalse(actions.updateDynamicRole());
        assertFalse(actions.updateEntitlements());
    }

    @Test
    public void shouldUpdate_ProfileAndEmails() {
        final String uuid = UUID.randomUUID().toString();
        var user = buildUser(uuid);
        var user2 = buildUser(uuid);
        Attribute attribute = new Attribute();
        attribute.setPrimary(true);
        attribute.setType("attrType");
        attribute.setValue(UUID.randomUUID().toString());
        user2.setEmails(Arrays.asList(attribute));

        var actions = UpdateActions.build(user, user2);
        assertTrue(actions.updateAttributes());
        assertTrue(actions.updateRequire());

        assertFalse(actions.updateRole());
        assertFalse(actions.updateAddresses());
        assertFalse(actions.updateDynamicRole());
        assertFalse(actions.updateEntitlements());
    }

    @Test
    public void shouldUpdate_ProfileAndPhoneNumbers() {
        final String uuid = UUID.randomUUID().toString();
        var user = buildUser(uuid);
        var user2 = buildUser(uuid);
        Attribute attribute = new Attribute();
        attribute.setPrimary(true);
        attribute.setType("attrType");
        attribute.setValue(UUID.randomUUID().toString());
        user2.setPhoneNumbers(Arrays.asList(attribute));

        var actions = UpdateActions.build(user, user2);
        assertTrue(actions.updateAttributes());
        assertTrue(actions.updateRequire());

        assertFalse(actions.updateRole());
        assertFalse(actions.updateAddresses());
        assertFalse(actions.updateDynamicRole());
        assertFalse(actions.updateEntitlements());
    }

    @Test
    public void shouldUpdate_ProfileAndIms() {
        final String uuid = UUID.randomUUID().toString();
        var user = buildUser(uuid);
        var user2 = buildUser(uuid);
        Attribute attribute = new Attribute();
        attribute.setPrimary(true);
        attribute.setType("attrType");
        attribute.setValue(UUID.randomUUID().toString());
        user2.setIms(Arrays.asList(attribute));

        var actions = UpdateActions.build(user, user2);
        assertTrue(actions.updateAttributes());
        assertTrue(actions.updateRequire());

        assertFalse(actions.updateRole());
        assertFalse(actions.updateAddresses());
        assertFalse(actions.updateDynamicRole());
        assertFalse(actions.updateEntitlements());
    }

    @Test
    public void shouldUpdate_ProfileAndPhotos() {
        final String uuid = UUID.randomUUID().toString();
        var user = buildUser(uuid);
        var user2 = buildUser(uuid);
        Attribute attribute = new Attribute();
        attribute.setPrimary(true);
        attribute.setType("attrType");
        attribute.setValue(UUID.randomUUID().toString());
        user2.setPhotos(Arrays.asList(attribute));

        var actions = UpdateActions.build(user, user2);
        assertTrue(actions.updateAttributes());
        assertTrue(actions.updateRequire());

        assertFalse(actions.updateRole());
        assertFalse(actions.updateAddresses());
        assertFalse(actions.updateDynamicRole());
        assertFalse(actions.updateEntitlements());
    }

    private User buildUser(String random) {
        User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domain"+random);
        user.setUsername("username"+random);
        user.setEmail(random+"@acme.fr");
        user.setAccountLockedAt(new Date());
        user.setAccountLockedUntil(new Date());
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setClient("client"+random);
        user.setCreatedAt(new Date());
        user.setMfaEnrollmentSkippedAt(new Date());
        user.setCredentialsNonExpired(true);
        user.setDisplayName("display"+random);
        user.setEnabled(true);
        user.setExternalId("external"+random);
        user.setInternal(false);
        user.setLastName("last"+random);
        user.setLoggedAt(new Date());
        user.setLastPasswordReset(new Date());
        user.setFirstName("first"+random);
        user.setLoginsCount(5l);
        user.setNewsletter(false);
        user.setNickName("nick"+random);
        user.setSource("test");

        Attribute attribute = new Attribute();
        attribute.setPrimary(true);
        attribute.setType("attrType");
        attribute.setValue("val"+random);
        user.setEmails(Arrays.asList(attribute));
        user.setPhotos(Arrays.asList(attribute));
        user.setPhoneNumbers(Arrays.asList(attribute));
        user.setIms(Arrays.asList(attribute));

        user.setEntitlements(Arrays.asList("ent"+random));
        user.setRoles(Arrays.asList("role"+random));
        user.setDynamicRoles(Arrays.asList("dynamic_role"+random));

        Address addr = new Address();
        addr.setCountry("fr");
        user.setAddresses(Arrays.asList(addr));

        Certificate certificate = new Certificate();
        certificate.setValue("cert"+random);
        user.setX509Certificates(Arrays.asList(certificate));

        EnrolledFactor fact = new EnrolledFactor();
        fact.setAppId("app"+random);
        fact.setSecurity(new EnrolledFactorSecurity("a", "b", Collections.singletonMap("a", "b")));
        fact.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, "e@e"));
        user.setFactors(Arrays.asList(fact));

        Map<String, Object> info = new HashMap<>();
        info.put(StandardClaims.EMAIL, random+"@info.acme.fr");
        info.put(StandardClaims.LOCALE, "en");
        user.setAdditionalInformation(info);
        return user;
    }
}
