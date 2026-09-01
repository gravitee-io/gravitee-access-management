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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.KeyRetrievalSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.am.service.model.PatchKeyRetrievalSettings;
import io.gravitee.am.service.model.openid.PatchOIDCSettings;
import io.gravitee.am.service.model.openid.PatchSpiffeDomainSettings;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * Tests verifying that the relocated trusted-domain key retrieval settings travel through the REST
 * layer on PATCH /domains/{id}, and that the limits' former home is still accepted.
 *
 * @author GraviteeSource Team
 */
public class DomainKeyRetrievalSettingsResourceTest extends JerseySpringTest {

    @BeforeEach
    void resetDomainServiceMock() {
        Mockito.reset(domainService);
    }

    @Test
    public void shouldPatchDomain_withKeyRetrievalSettings_serviceReceivesCorrectPatch() {
        Domain mockDomain = buildDomain();
        PatchDomain patchDomain = patchWithKeyRetrievalSettings();

        PatchDomain capturedDomain = capturePatchedDomain(mockDomain, patchDomain);

        assertNotNull("keyRetrievalSettings must be present", capturedDomain.getKeyRetrievalSettings());
        assertTrue("keyRetrievalSettings optional must be non-empty", capturedDomain.getKeyRetrievalSettings().isPresent());

        PatchKeyRetrievalSettings captured = capturedDomain.getKeyRetrievalSettings().get();
        assertTrue("allowPrivateIpAddress must be true", captured.getAllowPrivateIpAddress().get());
        assertEquals("fetchTimeoutMs must be 1234", Integer.valueOf(1234), captured.getFetchTimeoutMs().get());
        assertEquals("maxResponseSizeKb must be 64", Integer.valueOf(64), captured.getMaxResponseSizeKb().get());
        assertEquals("cacheTtlSeconds must be 60", Integer.valueOf(60), captured.getCacheTtlSeconds().get());
        assertEquals("cacheMaxEntries must be 10", Integer.valueOf(10), captured.getCacheMaxEntries().get());
    }

    @Test
    public void shouldPatchDomain_withKeyRetrievalSettings_responseContainsKeyRetrievalSettings() {
        Domain mockDomain = buildDomain();

        final Response response = patchDomain(mockDomain, patchWithKeyRetrievalSettings());

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        KeyRetrievalSettings settings = readEntity(response, Domain.class).getKeyRetrievalSettings();
        assertNotNull("keyRetrievalSettings must be present in response", settings);
        assertTrue("allowPrivateIpAddress must be true", settings.isAllowPrivateIpAddress());
        assertEquals("fetchTimeoutMs must be 1234", 1234, settings.getFetchTimeoutMs());
        assertEquals("cacheMaxEntries must be 10", 10, settings.getCacheMaxEntries());
    }

    @Test
    public void shouldPatchDomain_withDeprecatedSpiffeLimits_serviceStillReceivesThem() {
        Domain mockDomain = buildDomain();

        PatchSpiffeDomainSettings patchSpiffe = new PatchSpiffeDomainSettings();
        patchSpiffe.setEnabled(Optional.of(true));
        patchSpiffe.setFetchTimeoutMs(Optional.of(1234));
        PatchOIDCSettings patchOidc = new PatchOIDCSettings();
        patchOidc.setWorkloadIdentitySettings(Optional.of(patchSpiffe));
        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setOidc(Optional.of(patchOidc));

        PatchDomain capturedDomain = capturePatchedDomain(mockDomain, patchDomain);

        PatchSpiffeDomainSettings capturedSpiffe =
                capturedDomain.getOidc().get().getWorkloadIdentitySettings().get();
        assertTrue("enabled must be true", capturedSpiffe.getEnabled().get());
        assertEquals("fetchTimeoutMs must survive the deprecated field",
                Integer.valueOf(1234), capturedSpiffe.getFetchTimeoutMs().get());
        assertEquals("the deprecated limit must land in the relocated block",
                1234, capturedDomain.patch(new Domain()).getKeyRetrievalSettings().getFetchTimeoutMs());
    }

    @Test
    public void shouldPatchDomain_withKeyRetrievalSettings_triggersDomainSettingsPermission() {
        assertTrue("DOMAIN_SETTINGS permission must be required when keyRetrievalSettings is set",
                patchWithKeyRetrievalSettings().getRequiredPermissions().contains(Permission.DOMAIN_SETTINGS));
    }

    @Test
    public void shouldReadDomain_withoutRelocatedLimitsOnTheSpiffeBlock() {
        Domain mockDomain = buildDomain();

        final Response response = patchDomain(mockDomain, patchWithKeyRetrievalSettings());

        SpiffeDomainSettings spiffe = readEntity(response, Domain.class).getOidc().getWorkloadIdentitySettings();
        assertTrue("SPIFFE must stay enabled", spiffe.isEnabled());
        assertEquals("clock skew must stay on the SPIFFE block", 15, spiffe.getClockSkewSeconds());
        assertNull("the relocated limit must not come back on the SPIFFE block", spiffe.getFetchTimeoutMs());
    }

    private PatchDomain capturePatchedDomain(Domain mockDomain, PatchDomain patchDomain) {
        final Response response = patchDomain(mockDomain, patchDomain);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        ArgumentCaptor<PatchDomain> captor = ArgumentCaptor.forClass(PatchDomain.class);
        verify(domainService).patch(any(GraviteeContext.class), eq(mockDomain.getId()), captor.capture(), any(User.class));

        return captor.getValue();
    }

    private Response patchDomain(Domain mockDomain, PatchDomain patchDomain) {
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN)))
                .when(permissionService).findAllPermissions(any(User.class), any(), anyString());
        doReturn(Single.just(mockDomain))
                .when(domainService).patch(any(GraviteeContext.class), eq(mockDomain.getId()), any(PatchDomain.class), any(User.class));

        return patch(target("domains").path(mockDomain.getId()), patchDomain);
    }

    private static PatchDomain patchWithKeyRetrievalSettings() {
        PatchKeyRetrievalSettings patchKeyRetrieval = new PatchKeyRetrievalSettings();
        patchKeyRetrieval.setAllowUnsecuredHttpUri(Optional.of(false));
        patchKeyRetrieval.setAllowPrivateIpAddress(Optional.of(true));
        patchKeyRetrieval.setFetchTimeoutMs(Optional.of(1234));
        patchKeyRetrieval.setMaxResponseSizeKb(Optional.of(64));
        patchKeyRetrieval.setCacheTtlSeconds(Optional.of(60));
        patchKeyRetrieval.setCacheMaxEntries(Optional.of(10));

        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setKeyRetrievalSettings(Optional.of(patchKeyRetrieval));

        return patchDomain;
    }

    private static Domain buildDomain() {
        KeyRetrievalSettings keyRetrievalSettings = new KeyRetrievalSettings();
        keyRetrievalSettings.setAllowPrivateIpAddress(true);
        keyRetrievalSettings.setFetchTimeoutMs(1234);
        keyRetrievalSettings.setMaxResponseSizeKb(64);
        keyRetrievalSettings.setCacheTtlSeconds(60);
        keyRetrievalSettings.setCacheMaxEntries(10);

        SpiffeDomainSettings spiffeSettings = new SpiffeDomainSettings();
        spiffeSettings.setEnabled(true);
        spiffeSettings.setClockSkewSeconds(15);

        OIDCSettings oidcSettings = new OIDCSettings();
        oidcSettings.setWorkloadIdentitySettings(spiffeSettings);

        final Domain mockDomain = new Domain();
        mockDomain.setId("domain-id");
        mockDomain.setName("domain-name");
        mockDomain.setEnabled(true);
        mockDomain.setCreatedAt(new Date());
        mockDomain.setUpdatedAt(new Date());
        mockDomain.setPath("/path");
        mockDomain.setReferenceType(ReferenceType.ENVIRONMENT);
        mockDomain.setReferenceId("referenceId");
        mockDomain.setOidc(oidcSettings);
        mockDomain.setKeyRetrievalSettings(keyRetrievalSettings);

        return mockDomain;
    }
}
