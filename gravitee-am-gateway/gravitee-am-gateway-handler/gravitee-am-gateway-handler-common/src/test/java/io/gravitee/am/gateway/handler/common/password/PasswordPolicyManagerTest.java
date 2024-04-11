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

package io.gravitee.am.gateway.handler.common.password;


import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.oidc.Client;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordPolicyManagerTest {
    private final String POLICY_ID_1 = "policyId1";
    private final String POLICY_ID_2 = "policyId2";
    private final String POLICY_ID_3 = "policyId3";

    private PasswordPolicyManager passwordPolicyManager = new PasswordPolicyManagerImpl();

    private Map<String, PasswordPolicy> policies;

    @Before
    public void initPolicies() {
        resetPolicies();

        final var policy1 = new PasswordPolicy();
        policy1.setId(POLICY_ID_1);
        policy1.setDefaultPolicy(false);
        policy1.setCreatedAt(new Date(Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        final var policy2 = new PasswordPolicy();
        policy2.setId(POLICY_ID_2);
        policy2.setDefaultPolicy(true);
        policy2.setCreatedAt(new Date(Instant.now().minus(15, ChronoUnit.MINUTES).toEpochMilli()));

        final var policy3 = new PasswordPolicy();
        policy3.setId(POLICY_ID_3);
        policy3.setDefaultPolicy(false);
        policy3.setCreatedAt(new Date(Instant.now().minus(5, ChronoUnit.MINUTES).toEpochMilli()));

        policies.putAll(Map.of(POLICY_ID_1, policy1, POLICY_ID_2, policy2, POLICY_ID_3, policy3));
    }

    private void resetPolicies() {
        policies = (Map<String, PasswordPolicy>) ReflectionTestUtils.getField(passwordPolicyManager, "policies");
        policies.clear();
    }

    @Test
    public void shouldGetDefaultPolicy_whenClientAndIdp_notDefined() {
        var optPolicy = passwordPolicyManager.getPolicy(new Client(), new IdentityProvider());
        assertTrue(optPolicy.isPresent());
        assertEquals(POLICY_ID_2, optPolicy.get().getId());
    }

    @Test
    public void shouldNotGetPolicy_nothingDefined() {
        resetPolicies();
        var optPolicy = passwordPolicyManager.getPolicy(new Client(), new IdentityProvider());
        assertTrue(optPolicy.isEmpty());
    }

    @Test
    public void shouldGetIdpPolicy() {
        var identityProvider = new IdentityProvider();
        identityProvider.setPasswordPolicy(POLICY_ID_1);

        var optPolicy = passwordPolicyManager.getPolicy(new Client(), identityProvider);
        assertTrue(optPolicy.isPresent());
        assertEquals(POLICY_ID_1, optPolicy.get().getId());
    }

    @Test
    public void shouldGetIdpPolicy_evenIf_appDefineSettings() {
        var identityProvider = new IdentityProvider();
        identityProvider.setPasswordPolicy(POLICY_ID_3);

        var client = new Client();
        var passwordSettings = new PasswordSettings();
        passwordSettings.setInherited(true);
        client.setPasswordSettings(passwordSettings);

        var optPolicy = passwordPolicyManager.getPolicy(client, identityProvider);
        assertTrue(optPolicy.isPresent());
        assertEquals(POLICY_ID_3, optPolicy.get().getId());
    }

    @Test
    public void shouldGetAppSettings_domainInherited() {
        var identityProvider = new IdentityProvider();

        var client = new Client();
        var passwordSettings = new PasswordSettings();
        passwordSettings.setInherited(true);
        client.setPasswordSettings(passwordSettings);

        var optPolicy = passwordPolicyManager.getPolicy(client, identityProvider);
        assertTrue(optPolicy.isPresent());
        assertEquals(POLICY_ID_2, optPolicy.get().getId());
    }

    @Test
    public void shouldGetAppSettings_domainNotInherited() {
        var identityProvider = new IdentityProvider();

        var client = new Client();
        var passwordSettings = new PasswordSettings();
        passwordSettings.setInherited(false);
        passwordSettings.setMaxLength(124);
        client.setPasswordSettings(passwordSettings);

        var optPolicy = passwordPolicyManager.getPolicy(client, identityProvider);
        assertTrue(optPolicy.isPresent());
        assertNull(optPolicy.get().getId());
        assertEquals(passwordSettings.getMaxLength(), optPolicy.get().getMaxLength());
    }
}
