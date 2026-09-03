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
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.DeviceFlowSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.am.service.model.openid.PatchDeviceFlowSettings;
import io.gravitee.am.service.model.openid.PatchOIDCSettings;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * Tests verifying that Device Flow settings (DeviceFlowSettings / PatchDeviceFlowSettings) are
 * correctly serialised through the REST layer for PUT and PATCH on /domains/{id}.
 *
 * @author GraviteeSource Team
 */
public class DomainDeviceFlowSettingsResourceTest extends JerseySpringTest {

    @BeforeEach
    void resetDomainServiceMock() {
        Mockito.reset(domainService);
    }

    @Test
    public void shouldPatchDomainWithDeviceFlowSettings() {
        Domain mockDomain = buildDomainWithDeviceFlow();
        PatchDomain patchDomain = buildPatchDomainWithDeviceFlow();
        stubDomainService(mockDomain);

        final Response response = patch(target("domains").path(mockDomain.getId()), patchDomain);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        ArgumentCaptor<PatchDomain> captor = ArgumentCaptor.forClass(PatchDomain.class);
        verify(domainService).patch(any(GraviteeContext.class), eq(mockDomain.getId()), captor.capture(), any(User.class));

        PatchDomain captured = captor.getValue();
        assertNotNull("oidc section must be present", captured.getOidc());
        assertTrue("oidc optional must be non-empty", captured.getOidc().isPresent());

        PatchOIDCSettings capturedOidc = captured.getOidc().get();
        assertNotNull("deviceFlowSettings must be present in PatchOIDCSettings", capturedOidc.getDeviceFlowSettings());
        assertTrue("deviceFlowSettings optional must be non-empty", capturedOidc.getDeviceFlowSettings().isPresent());

        PatchDeviceFlowSettings capturedDeviceFlow = capturedOidc.getDeviceFlowSettings().get();
        assertTrue("enabled must be true", capturedDeviceFlow.getEnabled().get());
        assertEquals("deviceCodeExpiry must be 900", Integer.valueOf(900), capturedDeviceFlow.getDeviceCodeExpiry().get());
        assertEquals("pollingInterval must be 10", Integer.valueOf(10), capturedDeviceFlow.getPollingInterval().get());
    }

    @Test
    public void shouldReturnDeviceFlowSettingsInPatchResponse() {
        Domain mockDomain = buildDomainWithDeviceFlow();
        PatchDomain patchDomain = buildPatchDomainWithDeviceFlow();
        stubDomainService(mockDomain);

        final Response response = patch(target("domains").path(mockDomain.getId()), patchDomain);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        assertNotNull("oidc must be present in response", domain.getOidc());

        DeviceFlowSettings deviceFlow = domain.getOidc().getDeviceFlowSettings();
        assertNotNull("deviceFlowSettings must be present in response", deviceFlow);
        assertTrue("enabled must be true", deviceFlow.isEnabled());
        assertEquals("deviceCodeExpiry must be 900", 900, deviceFlow.getDeviceCodeExpiry());
        assertEquals("pollingInterval must be 10", 10, deviceFlow.getPollingInterval());
    }

    @Test
    public void shouldPutDomainWithDeviceFlowSettings() {
        Domain mockDomain = buildDomainWithDeviceFlow();
        PatchDomain patchDomain = buildPatchDomainWithDeviceFlow();
        stubDomainService(mockDomain);

        final Response response = put(target("domains").path(mockDomain.getId()), patchDomain);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        DeviceFlowSettings deviceFlow = domain.getOidc().getDeviceFlowSettings();
        assertNotNull("deviceFlowSettings must be present in response", deviceFlow);
        assertTrue("enabled must be true", deviceFlow.isEnabled());
        assertEquals("deviceCodeExpiry must be 900", 900, deviceFlow.getDeviceCodeExpiry());
    }

    @Test
    public void shouldOmitDeviceFlowSettingsForDomainThatNeverSetThem() {
        Domain mockDomain = buildDomainWithDeviceFlow();
        mockDomain.getOidc().setDeviceFlowSettings(null);

        PatchDeviceFlowSettings patchDeviceFlow = new PatchDeviceFlowSettings();
        patchDeviceFlow.setEnabled(Optional.of(false));
        PatchOIDCSettings patchOidc = new PatchOIDCSettings();
        patchOidc.setDeviceFlowSettings(Optional.of(patchDeviceFlow));
        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setOidc(Optional.of(patchOidc));

        stubDomainService(mockDomain);

        final Response response = patch(target("domains").path(mockDomain.getId()), patchDomain);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        assertNull("deviceFlowSettings must stay absent", domain.getOidc().getDeviceFlowSettings());
        assertFalse(domain.useDeviceFlow());
    }

    @Test
    public void shouldGetDomainWithDeviceFlowSettings() {
        Domain mockDomain = buildDomainWithDeviceFlow();

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN)))
                .when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(mockDomain.getId());

        final Response response = target("domains").path(mockDomain.getId()).request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        DeviceFlowSettings deviceFlow = domain.getOidc().getDeviceFlowSettings();
        assertNotNull("deviceFlowSettings must be readable through GET", deviceFlow);
        assertTrue(deviceFlow.isEnabled());
        assertEquals(900, deviceFlow.getDeviceCodeExpiry());
        assertEquals(10, deviceFlow.getPollingInterval());
    }

    private void stubDomainService(Domain mockDomain) {
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN)))
                .when(permissionService).findAllPermissions(any(User.class), any(), anyString());
        doReturn(Single.just(mockDomain))
                .when(domainService).patch(any(GraviteeContext.class), eq(mockDomain.getId()), any(PatchDomain.class), any(User.class));
    }

    private Domain buildDomainWithDeviceFlow() {
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

        DeviceFlowSettings deviceFlowSettings = new DeviceFlowSettings();
        deviceFlowSettings.setEnabled(true);
        deviceFlowSettings.setDeviceCodeExpiry(900);
        deviceFlowSettings.setPollingInterval(10);

        OIDCSettings oidcSettings = new OIDCSettings();
        oidcSettings.setDeviceFlowSettings(deviceFlowSettings);
        mockDomain.setOidc(oidcSettings);

        return mockDomain;
    }

    private PatchDomain buildPatchDomainWithDeviceFlow() {
        PatchDeviceFlowSettings patchDeviceFlow = new PatchDeviceFlowSettings();
        patchDeviceFlow.setEnabled(Optional.of(true));
        patchDeviceFlow.setDeviceCodeExpiry(Optional.of(900));
        patchDeviceFlow.setPollingInterval(Optional.of(10));

        PatchOIDCSettings patchOidc = new PatchOIDCSettings();
        patchOidc.setDeviceFlowSettings(Optional.of(patchDeviceFlow));

        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setOidc(Optional.of(patchOidc));

        return patchDomain;
    }
}
