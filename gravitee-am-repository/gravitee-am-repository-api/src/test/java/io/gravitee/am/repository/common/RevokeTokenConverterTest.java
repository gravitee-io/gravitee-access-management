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
package io.gravitee.am.repository.common;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.model.token.RevokeType;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class RevokeTokenConverterTest {

    @Test
    public void shouldReturnNullWhenRevokeTokenIsNull() {
        RevokeToken revokeToken = RevokeTokenConverter.toRevokeToken(null);

        assertNull(revokeToken);
    }

    @Test
    public void shouldConvertCurrentFormat() {
        Map<String, Object> revokeTokenMap = new HashMap<>();
        revokeTokenMap.put("revokeType", RevokeType.BY_USER_AND_CLIENT.name());
        revokeTokenMap.put("domainId", "domain-id");
        revokeTokenMap.put("user", userData("user-id", "user-name", ReferenceType.DOMAIN, "domain-id"));
        revokeTokenMap.put("principal", userData("principal-id", "principal-name", ReferenceType.ORGANIZATION, "organization-id"));
        revokeTokenMap.put("application", applicationData("client-id", "application-id", "application-name"));

        RevokeToken revokeToken = RevokeTokenConverter.toRevokeToken(revokeTokenMap);

        assertNotNull(revokeToken);
        assertEquals(RevokeType.BY_USER_AND_CLIENT, revokeToken.getRevokeType());
        assertEquals("domain-id", revokeToken.getDomainId());

        assertNotNull(revokeToken.getUser());
        assertEquals("user-id", revokeToken.getUser().getUserId());
        assertEquals("user-name", revokeToken.getUser().getUsername());
        assertEquals(ReferenceType.DOMAIN, revokeToken.getUser().getReferenceType());
        assertEquals("domain-id", revokeToken.getUser().getReferenceId());

        assertNotNull(revokeToken.getPrincipal());
        assertEquals("principal-id", revokeToken.getPrincipal().getUserId());
        assertEquals("principal-name", revokeToken.getPrincipal().getUsername());
        assertEquals(ReferenceType.ORGANIZATION, revokeToken.getPrincipal().getReferenceType());
        assertEquals("organization-id", revokeToken.getPrincipal().getReferenceId());

        assertNotNull(revokeToken.getApplication());
        assertEquals("client-id", revokeToken.getApplication().getClientId());
        assertEquals("application-id", revokeToken.getApplication().getApplicationId());
        assertEquals("application-name", revokeToken.getApplication().getApplicationName());
    }

    @Test
    public void shouldConvertLegacyUserIdForBackwardCompatibility() {
        Map<String, Object> revokeTokenMap = new HashMap<>();
        revokeTokenMap.put("revokeType", RevokeType.BY_USER.name());
        revokeTokenMap.put("domainId", "domain-id");
        revokeTokenMap.put("userId", Map.of("id", "legacy-user-id", "externalId", "external-id", "source", "idp"));

        RevokeToken revokeToken = RevokeTokenConverter.toRevokeToken(revokeTokenMap);

        assertNotNull(revokeToken);
        assertNotNull(revokeToken.getUser());
        assertEquals("legacy-user-id", revokeToken.getUser().getUserId());
        assertEquals("legacy-user-id", revokeToken.getUser().getUsername());
        assertEquals(ReferenceType.DOMAIN, revokeToken.getUser().getReferenceType());
        assertEquals("domain-id", revokeToken.getUser().getReferenceId());
    }

    @Test
    public void shouldUseCurrentUserWhenLegacyUserIdIsAlsoPresent() {
        Map<String, Object> revokeTokenMap = new HashMap<>();
        revokeTokenMap.put("revokeType", RevokeType.BY_USER.name());
        revokeTokenMap.put("domainId", "domain-id");
        revokeTokenMap.put("user", userData("current-user-id", "current-user-name", ReferenceType.DOMAIN, "domain-id"));
        revokeTokenMap.put("userId", Map.of("id", "legacy-user-id", "externalId", "external-id", "source", "idp"));

        RevokeToken revokeToken = RevokeTokenConverter.toRevokeToken(revokeTokenMap);

        assertNotNull(revokeToken);
        assertNotNull(revokeToken.getUser());
        assertEquals("current-user-id", revokeToken.getUser().getUserId());
        assertEquals("current-user-name", revokeToken.getUser().getUsername());
    }

    @Test
    public void shouldConvertLegacyClientIdForBackwardCompatibility() {
        Map<String, Object> revokeTokenMap = new HashMap<>();
        revokeTokenMap.put("revokeType", RevokeType.BY_CLIENT.name());
        revokeTokenMap.put("domainId", "domain-id");
        revokeTokenMap.put("clientId", "legacy-client-id");

        RevokeToken revokeToken = RevokeTokenConverter.toRevokeToken(revokeTokenMap);

        assertNotNull(revokeToken);
        assertNotNull(revokeToken.getApplication());
        assertEquals("legacy-client-id", revokeToken.getApplication().getClientId());
        assertNull(revokeToken.getApplication().getApplicationId());
        assertNull(revokeToken.getApplication().getApplicationName());
    }

    @Test
    public void shouldUseCurrentApplicationWhenLegacyClientIdIsAlsoPresent() {
        Map<String, Object> revokeTokenMap = new HashMap<>();
        revokeTokenMap.put("revokeType", RevokeType.BY_CLIENT.name());
        revokeTokenMap.put("domainId", "domain-id");
        revokeTokenMap.put("application", applicationData("current-client-id", "application-id", "application-name"));
        revokeTokenMap.put("clientId", "legacy-client-id");

        RevokeToken revokeToken = RevokeTokenConverter.toRevokeToken(revokeTokenMap);

        assertNotNull(revokeToken);
        assertNotNull(revokeToken.getApplication());
        assertEquals("current-client-id", revokeToken.getApplication().getClientId());
        assertEquals("application-id", revokeToken.getApplication().getApplicationId());
        assertEquals("application-name", revokeToken.getApplication().getApplicationName());
    }

    private Map<String, String> userData(String userId, String username, ReferenceType referenceType, String referenceId) {
        Map<String, String> user = new HashMap<>();
        user.put("userId", userId);
        user.put("username", username);
        user.put("referenceType", referenceType.name());
        user.put("referenceId", referenceId);
        return user;
    }

    private Map<String, String> applicationData(String clientId, String applicationId, String applicationName) {
        Map<String, String> application = new HashMap<>();
        application.put("clientId", clientId);
        application.put("applicationId", applicationId);
        application.put("applicationName", applicationName);
        return application;
    }
}
