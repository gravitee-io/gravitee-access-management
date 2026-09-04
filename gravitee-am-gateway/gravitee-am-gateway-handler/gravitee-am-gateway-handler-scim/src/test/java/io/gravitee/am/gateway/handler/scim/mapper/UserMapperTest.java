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
package io.gravitee.am.gateway.handler.scim.mapper;

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.common.scim.Schema;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.Attribute;
import io.gravitee.am.gateway.handler.scim.model.GraviteeUser;
import io.gravitee.am.gateway.handler.scim.model.User;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserMapperTest {

    @Test
    public void shouldConvert_customGraviteeUser() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setAdditionalInformation(Collections.singletonMap("customClaim", "customValue"));
        User scimUser = UserMapper.convert(user, "/", false);
        assertTrue(scimUser.getSchemas().contains(Schema.SCHEMA_URI_CUSTOM_USER));
        assertTrue(((GraviteeUser) scimUser).getAdditionalInformation().containsKey("customClaim"));
    }

    @Test
    public void shouldConvert_customGraviteeUser_shouldNotContainStandardClaims() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setAdditionalInformation(standardClaims());
        User scimUser = UserMapper.convert(user, "/", false);
        assertTrue(scimUser.getSchemas().contains(Schema.SCHEMA_URI_USER));
        assertTrue(((GraviteeUser) scimUser).getAdditionalInformation() == null);
    }

    @Test
    public void shouldConvert_customGraviteeUser_shouldNotContainRestrictedClaims() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setAdditionalInformation(restrictedClaims());
        User scimUser = UserMapper.convert(user, "/", false);
        assertTrue(scimUser.getSchemas().contains(Schema.SCHEMA_URI_USER));
        assertTrue(((GraviteeUser) scimUser).getAdditionalInformation() == null);
    }

    @Test
    public void shouldConvert_customGraviteeUser_shouldNotContainAllClaims() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        Map<String, Object> additionalInformation = new HashMap<>(standardClaims());
        additionalInformation.putAll(restrictedClaims());
        user.setAdditionalInformation(additionalInformation);
        User scimUser = UserMapper.convert(user, "/", false);
        assertTrue(scimUser.getSchemas().contains(Schema.SCHEMA_URI_USER));
        assertTrue(((GraviteeUser) scimUser).getAdditionalInformation() == null);
    }

    private Map<String, Object> standardClaims() {
        return StandardClaims.claims()
                .stream()
                .collect(Collectors.toMap(s -> s, s -> s));
    }

    private Map<String, Object> restrictedClaims() {
        return List.of(Claims.AUTH_TIME, ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY, ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY)
                .stream()
                .collect(Collectors.toMap(s -> s, s -> s));
    }

    @Test
    public void shouldExtractClient_fromGraviteeUser() {
        GraviteeUser scimUser = new GraviteeUser();
        scimUser.setSchemas(List.of(Schema.SCHEMA_URI_USER, Schema.SCHEMA_URI_CUSTOM_USER));
        scimUser.setUserName("testuser");
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("client", "my-application-id");
        scimUser.setAdditionalInformation(additionalInfo);

        io.gravitee.am.model.User user = UserMapper.convert(scimUser);

        assertTrue(user.getClient().equals("my-application-id"));
        // client should be removed from additionalInformation
        assertFalse(user.getAdditionalInformation().containsKey("client"));
    }

    @Test
    public void shouldHandleNullClient_fromGraviteeUser() {
        GraviteeUser scimUser = new GraviteeUser();
        scimUser.setSchemas(List.of(Schema.SCHEMA_URI_USER, Schema.SCHEMA_URI_CUSTOM_USER));
        scimUser.setUserName("testuser");
        Map<String, Object> additionalInfo = new HashMap<>();
        scimUser.setAdditionalInformation(additionalInfo);

        io.gravitee.am.model.User user = UserMapper.convert(scimUser);

        assertTrue(user.getClient() == null);
    }

    @Test(expected = io.gravitee.am.service.exception.UserInvalidException.class)
    public void shouldThrowException_whenClientIsNotString() {
        GraviteeUser scimUser = new GraviteeUser();
        scimUser.setSchemas(List.of(Schema.SCHEMA_URI_USER, Schema.SCHEMA_URI_CUSTOM_USER));
        scimUser.setUserName("testuser");
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("client", 12345); // Integer instead of String
        scimUser.setAdditionalInformation(additionalInfo);

        UserMapper.convert(scimUser);
    }

    @Test(expected = io.gravitee.am.service.exception.UserInvalidException.class)
    public void shouldThrowException_whenClientIsBoolean() {
        GraviteeUser scimUser = new GraviteeUser();
        scimUser.setSchemas(List.of(Schema.SCHEMA_URI_USER, Schema.SCHEMA_URI_CUSTOM_USER));
        scimUser.setUserName("testuser");
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("client", true); // Boolean instead of String
        scimUser.setAdditionalInformation(additionalInfo);

        UserMapper.convert(scimUser);
    }

    @Test
    public void shouldExtractBothPreRegistrationAndClient() {
        GraviteeUser scimUser = new GraviteeUser();
        scimUser.setSchemas(List.of(Schema.SCHEMA_URI_USER, Schema.SCHEMA_URI_CUSTOM_USER));
        scimUser.setUserName("testuser");
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("preRegistration", true);
        additionalInfo.put("client", "my-app");
        scimUser.setAdditionalInformation(additionalInfo);

        io.gravitee.am.model.User user = UserMapper.convert(scimUser);

        assertTrue(user.isPreRegistration());
        assertTrue(user.getClient().equals("my-app"));
        assertTrue(user.getPassword() == null); // preRegistration clears password
        // Both should be removed from additionalInformation
        assertFalse(user.getAdditionalInformation().containsKey("preRegistration"));
        assertFalse(user.getAdditionalInformation().containsKey("client"));
    }

    @Test
    public void shouldConvert_emailWithoutValue_orderedBeforeThePrimaryEmail() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setEmail("real@example.com");
        user.setEmails(List.of(modelAttribute(null, "home", null), modelAttribute("real@example.com", null, true)));

        User scimUser = UserMapper.convert(user, "/", true);

        assertEquals(1, scimUser.getEmails().size());
        assertEquals("real@example.com", scimUser.getEmails().get(0).getValue());
    }

    @Test
    public void shouldConvert_emailWithoutValue_asTheOnlyEntry() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setEmails(List.of(modelAttribute(null, "home", null)));

        User scimUser = UserMapper.convert(user, "/", false);

        assertTrue(scimUser.getEmails().isEmpty());
    }

    @Test
    public void shouldConvert_emailWithoutValue_replacedByThePrimaryEmail() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setEmail("real@example.com");
        user.setEmails(List.of(modelAttribute(null, "home", null)));

        User scimUser = UserMapper.convert(user, "/", true);

        assertEquals(1, scimUser.getEmails().size());
        assertEquals("real@example.com", scimUser.getEmails().get(0).getValue());
        assertTrue(scimUser.getEmails().get(0).isPrimary());
    }

    @Test
    public void shouldConvert_multiValuedAttributesWithoutValue() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setPhoneNumbers(List.of(modelAttribute(null, "mobile", null), modelAttribute("+33600000000", "mobile", true)));
        user.setIms(List.of(modelAttribute("", "skype", null)));
        user.setPhotos(List.of(modelAttribute(null, "photo", null)));

        User scimUser = UserMapper.convert(user, "/", false);

        assertEquals(1, scimUser.getPhoneNumbers().size());
        assertEquals("+33600000000", scimUser.getPhoneNumbers().get(0).getValue());
        assertTrue(scimUser.getIms().isEmpty());
        assertTrue(scimUser.getPhotos().isEmpty());
    }

    @Test
    public void shouldConvert_keepEveryWellFormedEmail() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setEmail("primary@example.com");
        user.setEmails(List.of(modelAttribute("home@example.com", "home", false), modelAttribute("primary@example.com", "work", true)));

        User scimUser = UserMapper.convert(user, "/", false);

        assertEquals(List.of("home@example.com", "primary@example.com"),
                scimUser.getEmails().stream().map(Attribute::getValue).collect(Collectors.toList()));
    }

    @Test(expected = InvalidValueException.class)
    public void shouldRejectScimUser_whenAnEmailHasNoValue() {
        User scimUser = new User();
        scimUser.setUserName("testuser");
        scimUser.setEmails(List.of(new Attribute(null, "home"), new Attribute("real@example.com", null, true)));

        UserMapper.convert(scimUser);
    }

    @Test(expected = InvalidValueException.class)
    public void shouldRejectScimUser_whenAnEmailValueIsBlank() {
        User scimUser = new User();
        scimUser.setUserName("testuser");
        scimUser.setEmails(List.of(new Attribute(" ", "home")));

        UserMapper.convert(scimUser);
    }

    @Test(expected = InvalidValueException.class)
    public void shouldRejectScimUser_whenAPhoneNumberHasNoValue() {
        User scimUser = new User();
        scimUser.setUserName("testuser");
        scimUser.setPhoneNumbers(List.of(new Attribute(null, "mobile")));

        UserMapper.convert(scimUser);
    }

    @Test
    public void shouldAcceptScimUser_whenEveryMultiValuedAttributeHasAValue() {
        User scimUser = new User();
        scimUser.setUserName("testuser");
        scimUser.setEmails(List.of(new Attribute("home@example.com", "home"), new Attribute("real@example.com", "work", true)));
        scimUser.setPhoneNumbers(List.of(new Attribute("+33600000000", "mobile")));

        io.gravitee.am.model.User user = UserMapper.convert(scimUser);

        assertEquals("real@example.com", user.getEmail());
        assertEquals(2, user.getEmails().size());
        assertEquals(1, user.getPhoneNumbers().size());
    }

    private static io.gravitee.am.model.scim.Attribute modelAttribute(String value, String type, Boolean primary) {
        io.gravitee.am.model.scim.Attribute attribute = new io.gravitee.am.model.scim.Attribute();
        attribute.setValue(value);
        attribute.setType(type);
        attribute.setPrimary(primary);
        return attribute;
    }
}
