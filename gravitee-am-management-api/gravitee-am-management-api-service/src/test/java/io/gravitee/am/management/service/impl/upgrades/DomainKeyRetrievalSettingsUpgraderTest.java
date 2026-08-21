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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyRetrievalSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainKeyRetrievalSettingsUpgraderTest {

    @Mock
    private DomainService domainService;

    @InjectMocks
    private DomainKeyRetrievalSettingsUpgrader upgrader;

    @Test
    void shouldMoveCustomizedLimitsOutOfSpiffeBlock() {
        SpiffeDomainSettings spiffe = new SpiffeDomainSettings();
        spiffe.setEnabled(true);
        spiffe.setAllowUnsecuredHttpUri(true);
        spiffe.setAllowPrivateIpAddress(true);
        spiffe.setFetchTimeoutMs(1234);
        spiffe.setMaxResponseSizeKb(64);
        spiffe.setCacheTtlSeconds(60);
        spiffe.setCacheMaxEntries(10);
        spiffe.setClockSkewSeconds(15);

        Domain upgraded = upgrade(domainWith(spiffe));

        KeyRetrievalSettings relocated = upgraded.getConfiguredKeyRetrievalSettings();
        assertTrue(relocated.isAllowUnsecuredHttpUri());
        assertTrue(relocated.isAllowPrivateIpAddress());
        assertEquals(1234, relocated.getFetchTimeoutMs());
        assertEquals(64, relocated.getMaxResponseSizeKb());
        assertEquals(60, relocated.getCacheTtlSeconds());
        assertEquals(10, relocated.getCacheMaxEntries());
    }

    @Test
    void shouldLeaveSpiffeValidationPolicyInPlace() {
        SpiffeDomainSettings spiffe = new SpiffeDomainSettings();
        spiffe.setEnabled(true);
        spiffe.setClockSkewSeconds(15);
        spiffe.setMaxJwtLifetimeSeconds(120);
        spiffe.setFetchTimeoutMs(1234);

        SpiffeDomainSettings upgraded = upgrade(domainWith(spiffe)).getOidc().getWorkloadIdentitySettings();

        assertTrue(upgraded.isEnabled());
        assertEquals(15, upgraded.getClockSkewSeconds());
        assertEquals(120, upgraded.getMaxJwtLifetimeSeconds());
    }

    @Test
    void shouldClearRelocatedLimitsFromSpiffeBlock() {
        SpiffeDomainSettings spiffe = new SpiffeDomainSettings();
        spiffe.setFetchTimeoutMs(1234);
        spiffe.setAllowPrivateIpAddress(true);

        SpiffeDomainSettings upgraded = upgrade(domainWith(spiffe)).getOidc().getWorkloadIdentitySettings();

        assertFalse(upgraded.hasLegacyRetrievalSettings());
        assertNull(upgraded.getFetchTimeoutMs());
        assertNull(upgraded.getAllowPrivateIpAddress());
    }

    @Test
    void shouldKeepDocumentedDefaultsForDomainThatNeverCustomizedTheLimits() {
        SpiffeDomainSettings spiffe = new SpiffeDomainSettings();
        spiffe.setEnabled(true);
        spiffe.setAllowUnsecuredHttpUri(false);
        spiffe.setAllowPrivateIpAddress(false);
        spiffe.setFetchTimeoutMs(KeyRetrievalSettings.DEFAULT_FETCH_TIMEOUT_MS);
        spiffe.setMaxResponseSizeKb(KeyRetrievalSettings.DEFAULT_MAX_RESPONSE_SIZE_KB);
        spiffe.setCacheTtlSeconds(KeyRetrievalSettings.DEFAULT_CACHE_TTL_SECONDS);
        spiffe.setCacheMaxEntries(KeyRetrievalSettings.DEFAULT_CACHE_MAX_ENTRIES);

        KeyRetrievalSettings relocated = upgrade(domainWith(spiffe)).getConfiguredKeyRetrievalSettings();

        assertEquals(KeyRetrievalSettings.DEFAULT_FETCH_TIMEOUT_MS, relocated.getFetchTimeoutMs());
        assertEquals(KeyRetrievalSettings.DEFAULT_MAX_RESPONSE_SIZE_KB, relocated.getMaxResponseSizeKb());
        assertEquals(KeyRetrievalSettings.DEFAULT_CACHE_TTL_SECONDS, relocated.getCacheTtlSeconds());
        assertEquals(KeyRetrievalSettings.DEFAULT_CACHE_MAX_ENTRIES, relocated.getCacheMaxEntries());
        assertFalse(relocated.isAllowUnsecuredHttpUri());
    }

    @Test
    void shouldKeepLimitDeliberatelySetToZero() {
        SpiffeDomainSettings spiffe = new SpiffeDomainSettings();
        spiffe.setFetchTimeoutMs(0);

        KeyRetrievalSettings relocated = upgrade(domainWith(spiffe)).getConfiguredKeyRetrievalSettings();

        assertEquals(0, relocated.getFetchTimeoutMs());
    }

    @Test
    void shouldNotRewriteDomainWithNoLimitsToRelocate() {
        SpiffeDomainSettings spiffe = new SpiffeDomainSettings();
        spiffe.setEnabled(true);
        Domain domain = domainWith(spiffe);
        when(domainService.listAll()).thenReturn(Flowable.just(domain));

        assertTrue(upgrader.upgrade());

        verify(domainService, never()).update(any(), any());
        assertEquals(KeyRetrievalSettings.DEFAULT_FETCH_TIMEOUT_MS,
                domain.getKeyRetrievalSettings().getFetchTimeoutMs());
    }

    @Test
    void shouldNotTouchAlreadyRelocatedDomain() {
        SpiffeDomainSettings spiffe = new SpiffeDomainSettings();
        spiffe.setEnabled(true);
        Domain domain = domainWith(spiffe);
        domain.getOidc().getWorkloadIdentitySettings().clearLegacyRetrievalSettings();
        KeyRetrievalSettings relocated = new KeyRetrievalSettings();
        relocated.setFetchTimeoutMs(999);
        domain.setKeyRetrievalSettings(relocated);

        when(domainService.listAll()).thenReturn(Flowable.just(domain));

        assertTrue(upgrader.upgrade());

        verify(domainService, never()).update(any(), any());
    }

    @Test
    void shouldNotTouchDomainThatNeverCarriedSpiffeSettings() {
        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setOidc(new OIDCSettings());
        when(domainService.listAll()).thenReturn(Flowable.just(domain));

        assertTrue(upgrader.upgrade());

        verify(domainService, never()).update(any(), any());
    }

    @Test
    void shouldNotTouchDomainWithoutOidcSettings() {
        Domain domain = new Domain();
        domain.setId("domain-id");
        when(domainService.listAll()).thenReturn(Flowable.just(domain));

        assertTrue(upgrader.upgrade());

        verify(domainService, never()).update(any(), any());
    }

    @Test
    void shouldKeepRelocatedValuesWhenRerun() {
        SpiffeDomainSettings spiffe = new SpiffeDomainSettings();
        spiffe.setFetchTimeoutMs(1234);

        Domain firstRun = upgrade(domainWith(spiffe));
        clearInvocations(domainService);

        when(domainService.listAll()).thenReturn(Flowable.just(firstRun));
        assertTrue(upgrader.upgrade());

        verify(domainService, never()).update(any(), any());
        assertEquals(1234, firstRun.getConfiguredKeyRetrievalSettings().getFetchTimeoutMs());
    }

    private Domain upgrade(Domain domain) {
        when(domainService.listAll()).thenReturn(Flowable.just(domain));
        ArgumentCaptor<Domain> captor = ArgumentCaptor.forClass(Domain.class);
        when(domainService.update(any(), captor.capture())).thenAnswer(i -> Single.just(i.getArgument(1)));
        assertTrue(upgrader.upgrade());
        return captor.getValue();
    }

    private static Domain domainWith(SpiffeDomainSettings spiffe) {
        OIDCSettings oidc = new OIDCSettings();
        oidc.setWorkloadIdentitySettings(spiffe);
        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setOidc(oidc);
        return domain;
    }
}
