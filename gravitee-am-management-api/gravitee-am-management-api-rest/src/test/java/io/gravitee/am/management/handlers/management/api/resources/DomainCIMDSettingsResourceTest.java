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
import io.gravitee.am.model.CertificateSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.am.service.model.openid.PatchCIMDSettings;
import io.gravitee.am.service.model.openid.PatchOIDCSettings;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * Tests verifying that CIMD settings (CIMDSettings / PatchCIMDSettings) are
 * correctly serialised through the REST layer for PUT and PATCH on /domains/{id}.
 *
 * @author GraviteeSource Team
 */
public class DomainCIMDSettingsResourceTest extends JerseySpringTest {

    /**
     * Reset domainService mock between tests so verify(…, times(1)) counts are not cumulative.
     */
    @BeforeEach
    void resetDomainServiceMock() {
        Mockito.reset(domainService);
    }

    // -------------------------------------------------------------------------
    // PUT – full domain update carrying CIMD settings
    // -------------------------------------------------------------------------

    @Test
    public void shouldPutDomain_withCIMDSettings_serviceReceivesCorrectPatch() {

        Domain mockDomain = buildDomainWithCIMD();

        PatchDomain patchDomain = buildPatchDomainWithCIMD();

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN)))
                .when(permissionService).findAllPermissions(any(User.class), any(), anyString());
        doReturn(Single.just(mockDomain))
                .when(domainService).patch(any(GraviteeContext.class), eq(mockDomain.getId()), any(PatchDomain.class), any(User.class));

        final Response response = put(target("domains").path(mockDomain.getId()), patchDomain);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        // Verify the PatchDomain that reached the service actually contains CIMD settings
        ArgumentCaptor<PatchDomain> captor = ArgumentCaptor.forClass(PatchDomain.class);
        verify(domainService).patch(any(GraviteeContext.class), eq(mockDomain.getId()), captor.capture(), any(User.class));

        PatchDomain captured = captor.getValue();
        assertNotNull("oidc section must be present", captured.getOidc());
        assertTrue("oidc optional must be non-empty", captured.getOidc().isPresent());

        PatchOIDCSettings capturedOidc = captured.getOidc().get();
        assertNotNull("cimdSettings must be present in PatchOIDCSettings", capturedOidc.getCimdSettings());
        assertTrue("cimdSettings optional must be non-empty", capturedOidc.getCimdSettings().isPresent());

        PatchCIMDSettings capturedCimd = capturedOidc.getCimdSettings().get();
        assertTrue("enabled must be present", capturedCimd.getEnabled().isPresent());
        assertTrue("enabled value must be true", capturedCimd.getEnabled().get());
        assertTrue("allowUnsecuredHttpUri must be present", capturedCimd.getAllowUnsecuredHttpUri().isPresent());
        assertTrue("allowUnsecuredHttpUri value must be true", capturedCimd.getAllowUnsecuredHttpUri().get());
        assertTrue("allowPrivateIpAddress must be present", capturedCimd.getAllowPrivateIpAddress().isPresent());
        assertTrue("fetchTimeoutMs must be present", capturedCimd.getFetchTimeoutMs().isPresent());
        assertEquals("fetchTimeoutMs must be 3000", Integer.valueOf(3000), capturedCimd.getFetchTimeoutMs().get());
        assertTrue("maxResponseSizeKb must be present", capturedCimd.getMaxResponseSizeKb().isPresent());
        assertEquals("maxResponseSizeKb must be 20", Integer.valueOf(20), capturedCimd.getMaxResponseSizeKb().get());
        assertTrue("allowedDomains must be present", capturedCimd.getAllowedDomains().isPresent());
        assertEquals("allowedDomains must contain 2 entries", 2, capturedCimd.getAllowedDomains().get().size());
        assertTrue("cacheTtlSeconds must be present", capturedCimd.getCacheTtlSeconds().isPresent());
        assertEquals("cacheTtlSeconds must be 7200", Integer.valueOf(7200), capturedCimd.getCacheTtlSeconds().get());
        assertTrue("cacheMaxEntries must be present", capturedCimd.getCacheMaxEntries().isPresent());
        assertEquals("cacheMaxEntries must be 500", Integer.valueOf(500), capturedCimd.getCacheMaxEntries().get());
        assertTrue("softwareId must be present", capturedCimd.getSoftwareId().isPresent());
        assertEquals("softwareId must match", "my-software-id", capturedCimd.getSoftwareId().get());
    }

    @Test
    public void shouldPutDomain_withCIMDSettings_responseContainsCIMDSettings() {

        Domain mockDomain = buildDomainWithCIMD();

        PatchDomain patchDomain = buildPatchDomainWithCIMD();

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN)))
                .when(permissionService).findAllPermissions(any(User.class), any(), anyString());
        doReturn(Single.just(mockDomain))
                .when(domainService).patch(any(GraviteeContext.class), eq(mockDomain.getId()), any(PatchDomain.class), any(User.class));

        final Response response = put(target("domains").path(mockDomain.getId()), patchDomain);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        assertNotNull("oidc must be present in response", domain.getOidc());
        assertNotNull("cimdSettings must be present in response OIDCSettings", domain.getOidc().getCimdSettings());

        CIMDSettings cimd = domain.getOidc().getCimdSettings();
        assertTrue("enabled must be true", cimd.isEnabled());
        assertTrue("allowUnsecuredHttpUri must be true", cimd.isAllowUnsecuredHttpUri());
        assertEquals("fetchTimeoutMs must be 3000", 3000, cimd.getFetchTimeoutMs());
        assertEquals("maxResponseSizeKb must be 20", 20, cimd.getMaxResponseSizeKb());
        assertNotNull("allowedDomains must not be null", cimd.getAllowedDomains());
        assertEquals("allowedDomains must contain 2 entries", 2, cimd.getAllowedDomains().size());
        assertEquals("cacheTtlSeconds must be 7200", 7200, cimd.getCacheTtlSeconds());
        assertEquals("cacheMaxEntries must be 500", 500, cimd.getCacheMaxEntries());
        assertEquals("softwareId must match", "my-software-id", cimd.getSoftwareId());
    }

    // -------------------------------------------------------------------------
    // PATCH – partial domain update carrying CIMD settings
    // -------------------------------------------------------------------------

    @Test
    public void shouldPatchDomain_withCIMDSettings_serviceReceivesCorrectPatch() {

        Domain mockDomain = buildDomainWithCIMD();

        PatchDomain patchDomain = buildPatchDomainWithCIMD();

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN)))
                .when(permissionService).findAllPermissions(any(User.class), any(), anyString());
        doReturn(Single.just(mockDomain))
                .when(domainService).patch(any(GraviteeContext.class), eq(mockDomain.getId()), any(PatchDomain.class), any(User.class));

        final Response response = patch(target("domains").path(mockDomain.getId()), patchDomain);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        ArgumentCaptor<PatchDomain> captor = ArgumentCaptor.forClass(PatchDomain.class);
        verify(domainService).patch(any(GraviteeContext.class), eq(mockDomain.getId()), captor.capture(), any(User.class));

        PatchDomain captured = captor.getValue();
        assertNotNull("oidc section must be present", captured.getOidc());
        assertTrue("oidc optional must be non-empty", captured.getOidc().isPresent());

        PatchCIMDSettings capturedCimd = captured.getOidc().get().getCimdSettings().get();
        assertTrue("enabled must be true", capturedCimd.getEnabled().get());
        assertEquals("softwareId must match", "my-software-id", capturedCimd.getSoftwareId().get());
        assertEquals("fetchTimeoutMs must be 3000", Integer.valueOf(3000), capturedCimd.getFetchTimeoutMs().get());
        assertEquals("cacheTtlSeconds must be 7200", Integer.valueOf(7200), capturedCimd.getCacheTtlSeconds().get());
    }

    @Test
    public void shouldPatchDomain_withCIMDSettings_responseContainsCIMDSettings() {

        Domain mockDomain = buildDomainWithCIMD();

        PatchDomain patchDomain = buildPatchDomainWithCIMD();

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN)))
                .when(permissionService).findAllPermissions(any(User.class), any(), anyString());
        doReturn(Single.just(mockDomain))
                .when(domainService).patch(any(GraviteeContext.class), eq(mockDomain.getId()), any(PatchDomain.class), any(User.class));

        final Response response = patch(target("domains").path(mockDomain.getId()), patchDomain);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        assertNotNull("oidc must be present in response", domain.getOidc());

        CIMDSettings cimd = domain.getOidc().getCimdSettings();
        assertNotNull("cimdSettings must be present in response", cimd);
        assertTrue("enabled must be true", cimd.isEnabled());
        assertEquals("softwareId must match", "my-software-id", cimd.getSoftwareId());
        assertEquals("fetchTimeoutMs must be 3000", 3000, cimd.getFetchTimeoutMs());
        assertEquals("cacheTtlSeconds must be 7200", 7200, cimd.getCacheTtlSeconds());
    }

    @Test
    public void shouldPatchDomain_withOnlyCIMDEnabled_triggersOpenIdPermission() {

        Domain mockDomain = buildDomainWithCIMD();

        // Patch containing only 'enabled' flag for CIMD – minimal case
        PatchCIMDSettings patchCimd = new PatchCIMDSettings();
        patchCimd.setEnabled(Optional.of(true));

        PatchOIDCSettings patchOidc = new PatchOIDCSettings();
        patchOidc.setCimdSettings(Optional.of(patchCimd));

        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setOidc(Optional.of(patchOidc));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN)))
                .when(permissionService).findAllPermissions(any(User.class), any(), anyString());
        doReturn(Single.just(mockDomain))
                .when(domainService).patch(any(GraviteeContext.class), eq(mockDomain.getId()), any(PatchDomain.class), any(User.class));

        final Response response = patch(target("domains").path(mockDomain.getId()), patchDomain);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        // The required permission set must contain DOMAIN_OPENID since cimdSettings is present
        Set<Permission> requiredPermissions = patchDomain.getRequiredPermissions();
        assertTrue("DOMAIN_OPENID permission must be required when cimdSettings is set",
                requiredPermissions.contains(Permission.DOMAIN_OPENID));
    }

    private Domain buildDomainWithCIMD() {
        final Domain mockDomain = new Domain();
        mockDomain.setId("domain-id");
        mockDomain.setName("domain-name");
        mockDomain.setDescription("description");
        mockDomain.setEnabled(true);
        mockDomain.setCreatedAt(new Date());
        mockDomain.setUpdatedAt(new Date());
        mockDomain.setPath("/path");
        mockDomain.setReferenceType(ReferenceType.ENVIRONMENT);
        mockDomain.setReferenceId("referenceId");
        mockDomain.setScim(new SCIMSettings());
        mockDomain.setLoginSettings(new LoginSettings());
        mockDomain.setAccountSettings(new AccountSettings());
        mockDomain.setTags(Collections.singleton("tag"));

        CertificateSettings certificateSettings = new CertificateSettings();
        certificateSettings.setFallbackCertificate("fallback-cert-id");
        mockDomain.setCertificateSettings(certificateSettings);

        CIMDSettings cimdSettings = new CIMDSettings();
        cimdSettings.setEnabled(true);
        cimdSettings.setAllowUnsecuredHttpUri(true);
        cimdSettings.setAllowPrivateIpAddress(false);
        cimdSettings.setFetchTimeoutMs(3000);
        cimdSettings.setMaxResponseSizeKb(20);
        cimdSettings.setAllowedDomains(Arrays.asList("example.com", "*.trusted.io"));
        cimdSettings.setCacheTtlSeconds(7200);
        cimdSettings.setCacheMaxEntries(500);
        cimdSettings.setSoftwareId("my-software-id");

        OIDCSettings oidcSettings = new OIDCSettings();
        oidcSettings.setCimdSettings(cimdSettings);
        mockDomain.setOidc(oidcSettings);

        return mockDomain;
    }

    private PatchDomain buildPatchDomainWithCIMD() {
        PatchCIMDSettings patchCimd = new PatchCIMDSettings();
        patchCimd.setEnabled(Optional.of(true));
        patchCimd.setAllowUnsecuredHttpUri(Optional.of(true));
        patchCimd.setAllowPrivateIpAddress(Optional.of(false));
        patchCimd.setFetchTimeoutMs(Optional.of(3000));
        patchCimd.setMaxResponseSizeKb(Optional.of(20));
        patchCimd.setAllowedDomains(Optional.of(Arrays.asList("example.com", "*.trusted.io")));
        patchCimd.setCacheTtlSeconds(Optional.of(7200));
        patchCimd.setCacheMaxEntries(Optional.of(500));
        patchCimd.setSoftwareId(Optional.of("my-software-id"));

        PatchOIDCSettings patchOidc = new PatchOIDCSettings();
        patchOidc.setCimdSettings(Optional.of(patchCimd));

        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setOidc(Optional.of(patchOidc));

        return patchDomain;
    }
}
