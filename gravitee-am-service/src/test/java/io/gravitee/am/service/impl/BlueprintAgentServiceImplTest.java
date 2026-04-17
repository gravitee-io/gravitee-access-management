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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.AgentSettings;
import io.gravitee.am.model.application.ApplicationAdvancedSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.reporter.builder.AgentAuditBuilder;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BlueprintAgentServiceImplTest {

    @Mock
    private ApplicationService applicationService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private BlueprintAgentServiceImpl service;

    private String appId;
    private String domainId;
    private Application application;
    private ApplicationSettings appSettings;
    private ApplicationAdvancedSettings advancedSettings;
    private AgentSettings agentSettings;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID().toString();
        domainId = UUID.randomUUID().toString();

        // Setup application
        application = new Application();
        application.setId(appId);
        application.setDomain(domainId);

        // Setup settings hierarchy
        advancedSettings = new ApplicationAdvancedSettings();
        advancedSettings.setAgentIdentityMode(true);

        agentSettings = new AgentSettings();
        agentSettings.setMaxPublicKeysPerWorkload(10);

        appSettings = new ApplicationSettings();
        appSettings.setAdvanced(advancedSettings);
        appSettings.setAgent(agentSettings);

        application.setSettings(appSettings);
    }

    // ===== addAgentKey Tests =====

    @Test
    @DisplayName("Should add agent key to existing JWKS and emit keyAdded audit")
    void testAddAgentKeySuccess() {
        // Setup existing JWKS with one key
        JWKSet jwks = new JWKSet();
        List<JWK> keys = new ArrayList<>();
        JWK existingKey = buildTestJWK("existing-kid");
        keys.add(existingKey);
        jwks.setKeys(keys);
        agentSettings.setJwks(jwks);

        JWK newKey = buildTestJWK("new-kid");

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));
        given(applicationService.update(application)).willReturn(Single.just(application));

        var testObserver = service.addAgentKey(appId, newKey).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(application);

        // Verify the key was added to JWKS
        assertEquals(2, agentSettings.getJwks().getKeys().size());
        assertTrue(agentSettings.getJwks().getKeys().stream()
                .anyMatch(k -> "new-kid".equals(k.getKid())));

        // Verify audit was called
        ArgumentCaptor<AuditBuilder> auditCaptor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(auditCaptor.capture());
        verify(applicationService).update(application);
    }

    @Test
    @DisplayName("Should initialize null JWKS when adding first key")
    void testAddAgentKeyInitializesNullJwks() {
        agentSettings.setJwks(null);

        JWK newKey = buildTestJWK("first-kid");

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));
        given(applicationService.update(application)).willReturn(Single.just(application));

        var testObserver = service.addAgentKey(appId, newKey).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Verify JWKS was initialized
        assertNotNull(agentSettings.getJwks());
        assertEquals(1, agentSettings.getJwks().getKeys().size());
        assertEquals("first-kid", agentSettings.getJwks().getKeys().get(0).getKid());
    }

    @Test
    @DisplayName("Should initialize null keys list when JWKS exists but keys are null")
    void testAddAgentKeyInitializesNullKeysList() {
        JWKSet jwks = new JWKSet();
        jwks.setKeys(null);
        agentSettings.setJwks(jwks);

        JWK newKey = buildTestJWK("test-kid");

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));
        given(applicationService.update(application)).willReturn(Single.just(application));

        var testObserver = service.addAgentKey(appId, newKey).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        assertEquals(1, agentSettings.getJwks().getKeys().size());
    }

    @Test
    @DisplayName("Should reject null kid")
    void testAddAgentKeyRejectsNullKid() {
        agentSettings.setJwks(new JWKSet());
        agentSettings.getJwks().setKeys(new ArrayList<>());

        JWK keyWithoutKid = buildTestJWK(null);

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.addAgentKey(appId, keyWithoutKid).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(e -> "JWK must have a kid".equals(e.getMessage()));
    }

    @Test
    @DisplayName("Should reject blank kid")
    void testAddAgentKeyRejectsBlankKid() {
        agentSettings.setJwks(new JWKSet());
        agentSettings.getJwks().setKeys(new ArrayList<>());

        JWK keyWithBlankKid = buildTestJWK("  ");

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.addAgentKey(appId, keyWithBlankKid).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(e -> "JWK must have a kid".equals(e.getMessage()));
    }

    @Test
    @DisplayName("Should reject duplicate kid")
    void testAddAgentKeyRejectsDuplicateKid() {
        JWKSet jwks = new JWKSet();
        List<JWK> keys = new ArrayList<>();
        JWK existingKey = buildTestJWK("duplicate-kid");
        keys.add(existingKey);
        jwks.setKeys(keys);
        agentSettings.setJwks(jwks);

        JWK duplicateKey = buildTestJWK("duplicate-kid");

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.addAgentKey(appId, duplicateKey).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(e -> "A key with kid 'duplicate-kid' already exists".equals(e.getMessage()));
    }

    @Test
    @DisplayName("Should reject when max public keys limit reached")
    void testAddAgentKeyRejectsMaxKeysReached() {
        agentSettings.setMaxPublicKeysPerWorkload(2);

        JWKSet jwks = new JWKSet();
        List<JWK> keys = new ArrayList<>();
        keys.add(buildTestJWK("kid1"));
        keys.add(buildTestJWK("kid2"));
        jwks.setKeys(keys);
        agentSettings.setJwks(jwks);

        JWK newKey = buildTestJWK("kid3");

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.addAgentKey(appId, newKey).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(e -> "Maximum number of public keys (2) reached".equals(e.getMessage()));
    }

    @Test
    @DisplayName("Should throw ApplicationNotFoundException when app not found")
    void testAddAgentKeyAppNotFound() {
        JWK newKey = buildTestJWK("test-kid");

        given(applicationService.findById(appId)).willReturn(Maybe.error(new ApplicationNotFoundException(appId)));

        var testObserver = service.addAgentKey(appId, newKey).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(ApplicationNotFoundException.class);
    }

    @Test
    @DisplayName("Should reject when agentIdentityMode is false")
    void testAddAgentKeyRejectsAgentIdentityModeDisabled() {
        advancedSettings.setAgentIdentityMode(false);

        JWK newKey = buildTestJWK("test-kid");

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.addAgentKey(appId, newKey).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(e -> "Application is not in agent identity mode".equals(e.getMessage()));
    }

    @Test
    @DisplayName("Should reject when agent settings are null")
    void testAddAgentKeyRejectsNullAgentSettings() {
        appSettings.setAgent(null);

        JWK newKey = buildTestJWK("test-kid");

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.addAgentKey(appId, newKey).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(e -> "Application has no agent settings configured".equals(e.getMessage()));
    }

    @Test
    @DisplayName("Should reject when advanced settings are null")
    void testAddAgentKeyRejectsNullAdvancedSettings() {
        appSettings.setAdvanced(null);

        JWK newKey = buildTestJWK("test-kid");

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.addAgentKey(appId, newKey).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    @Test
    @DisplayName("Should reject when settings are null")
    void testAddAgentKeyRejectsNullSettings() {
        application.setSettings(null);

        JWK newKey = buildTestJWK("test-kid");

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.addAgentKey(appId, newKey).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
    }

    // ===== removeAgentKey Tests =====

    @Test
    @DisplayName("Should remove existing key and emit keyRemoved audit")
    void testRemoveAgentKeySuccess() {
        JWKSet jwks = new JWKSet();
        List<JWK> keys = new ArrayList<>();
        keys.add(buildTestJWK("kid-to-remove"));
        keys.add(buildTestJWK("kid-to-keep"));
        jwks.setKeys(keys);
        agentSettings.setJwks(jwks);

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));
        given(applicationService.update(application)).willReturn(Single.just(application));

        var testObserver = service.removeAgentKey(appId, "kid-to-remove").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(application);

        // Verify key was removed
        assertEquals(1, agentSettings.getJwks().getKeys().size());
        assertEquals("kid-to-keep", agentSettings.getJwks().getKeys().get(0).getKid());

        // Verify audit was called
        verify(auditService).report(isA(AuditBuilder.class));
        verify(applicationService).update(application);
    }

    @Test
    @DisplayName("Should error when kid not found")
    void testRemoveAgentKeyKidNotFound() {
        JWKSet jwks = new JWKSet();
        List<JWK> keys = new ArrayList<>();
        keys.add(buildTestJWK("existing-kid"));
        jwks.setKeys(keys);
        agentSettings.setJwks(jwks);

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.removeAgentKey(appId, "non-existent-kid").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(e -> "Key with kid 'non-existent-kid' not found".equals(e.getMessage()));
    }

    @Test
    @DisplayName("Should error when JWKS has no keys")
    void testRemoveAgentKeyNoKeysFound() {
        agentSettings.setJwks(null);

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.removeAgentKey(appId, "any-kid").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(e -> "No keys found on this application".equals(e.getMessage()));
    }

    @Test
    @DisplayName("Should error when JWKS keys list is null")
    void testRemoveAgentKeyKeysListNull() {
        JWKSet jwks = new JWKSet();
        jwks.setKeys(null);
        agentSettings.setJwks(jwks);

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.removeAgentKey(appId, "any-kid").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(e -> "No keys found on this application".equals(e.getMessage()));
    }

    @Test
    @DisplayName("Should throw ApplicationNotFoundException when app not found on remove")
    void testRemoveAgentKeyAppNotFound() {
        given(applicationService.findById(appId)).willReturn(Maybe.error(new ApplicationNotFoundException(appId)));

        var testObserver = service.removeAgentKey(appId, "any-kid").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(ApplicationNotFoundException.class);
    }

    // ===== listAgentKeys Tests =====

    @Test
    @DisplayName("Should return empty list when JWKS is null")
    void testListAgentKeysNullJwks() {
        agentSettings.setJwks(null);

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.listAgentKeys(appId).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(List.of());
    }

    @Test
    @DisplayName("Should return empty list when keys list is null")
    void testListAgentKeysNullKeysList() {
        JWKSet jwks = new JWKSet();
        jwks.setKeys(null);
        agentSettings.setJwks(jwks);

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.listAgentKeys(appId).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(List.of());
    }

    @Test
    @DisplayName("Should return all keys from populated JWKS")
    void testListAgentKeysPopulatedJwks() {
        JWKSet jwks = new JWKSet();
        List<JWK> keys = new ArrayList<>();
        keys.add(buildTestJWK("kid1"));
        keys.add(buildTestJWK("kid2"));
        keys.add(buildTestJWK("kid3"));
        jwks.setKeys(keys);
        agentSettings.setJwks(jwks);

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.listAgentKeys(appId).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        var result = testObserver.values().get(0);
        assertEquals(3, result.size());
        assertEquals("kid1", result.get(0).getKid());
        assertEquals("kid2", result.get(1).getKid());
        assertEquals("kid3", result.get(2).getKid());
    }

    @Test
    @DisplayName("Should propagate agent-settings-missing error on list")
    void testListAgentKeysPropagatesSettingsError() {
        appSettings.setAgent(null);

        given(applicationService.findById(appId)).willReturn(Maybe.just(application));

        var testObserver = service.listAgentKeys(appId).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertError(e -> "Application has no agent settings configured".equals(e.getMessage()));
    }

    @Test
    @DisplayName("Should throw ApplicationNotFoundException when app not found on list")
    void testListAgentKeysAppNotFound() {
        given(applicationService.findById(appId)).willReturn(Maybe.error(new ApplicationNotFoundException(appId)));

        var testObserver = service.listAgentKeys(appId).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(ApplicationNotFoundException.class);
    }

    // ===== Helper Methods =====

    private JWK buildTestJWK(String kid) {
        RSAKey key = new RSAKey();
        key.setKid(kid);
        key.setKty("RSA");
        key.setUse("sig");
        key.setAlg("RS256");
        return key;
    }
}
