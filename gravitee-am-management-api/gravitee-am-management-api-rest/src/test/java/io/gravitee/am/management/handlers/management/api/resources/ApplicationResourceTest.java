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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationAdvancedSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.ApplicationNotFoundException;
import io.gravitee.am.service.model.PatchApplication;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;

import javax.ws.rs.core.Response;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetApp() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application mockApplication = buildApplicationMock();

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.APPLICATION))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockApplication)).when(applicationService).findById(mockApplication.getId());

        // Check all data are returned when having all permissions.
        final Response response = target("domains").path(domainId).path("applications").path(mockApplication.getId()).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Application application = readEntity(response, Application.class);
        assertEquals(mockApplication.getId(), application.getId());
        assertEquals(mockApplication.getName(), application.getName());
        assertEquals(mockApplication.getDomain(), application.getDomain());
        assertEquals(mockApplication.getType(), application.getType());
        assertEquals(mockApplication.getDescription(), application.getDescription());
        assertEquals(mockApplication.isEnabled(), application.isEnabled());
        assertEquals(mockApplication.isTemplate(), application.isTemplate());
        assertEquals(mockApplication.getFactors(), application.getFactors());
        assertEquals(mockApplication.getCreatedAt(), application.getCreatedAt());
        assertEquals(mockApplication.getUpdatedAt(), application.getUpdatedAt());
        assertEquals(mockApplication.getIdentities(), application.getIdentities());
        assertEquals(mockApplication.getCertificate(), application.getCertificate());
        assertNotNull(application.getSettings());
        assertNotNull(application.getSettings().getAdvanced());
        assertNotNull(application.getSettings().getAccount());
        assertNotNull(application.getSettings().getOauth());
    }

    @Test
    public void shouldGetFilteredApp() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application mockApplication = buildApplicationMock();

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.of(Permission.APPLICATION, Acl.READ))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString()); // only application read permission
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockApplication)).when(applicationService).findById(mockApplication.getId());

        // Check data are filtered according to permissions.
        final Response response = target("domains").path(domainId).path("applications").path(mockApplication.getId()).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Application application = readEntity(response, Application.class);
        assertEquals(mockApplication.getId(), application.getId());
        assertEquals(mockApplication.getName(), application.getName());
        assertEquals(mockApplication.getDomain(), application.getDomain());
        assertEquals(mockApplication.getType(), application.getType());
        assertEquals(mockApplication.getDescription(), application.getDescription());
        assertEquals(mockApplication.isEnabled(), application.isEnabled());
        assertEquals(mockApplication.isTemplate(), application.isTemplate());
        assertNull(application.getFactors());
        assertEquals(mockApplication.getCreatedAt(), application.getCreatedAt());
        assertEquals(mockApplication.getUpdatedAt(), application.getUpdatedAt());
        assertNull(application.getIdentities());
        assertNull(application.getCertificate());
        assertNotNull(application.getSettings());
        assertNull(application.getSettings().getAccount());
        assertNull(application.getSettings().getOauth());
    }

    @Test
    public void shouldGetApplication_notFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(applicationService).findById(clientId);

        final Response response = target("domains").path(domainId).path("applications").path(clientId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetClient_domainNotFound() {
        final String domainId = "domain-id";
        final String clientId = "client-id";

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).path("applications").path(clientId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetApplication_wrongDomain() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String applicationId = "application-id";
        final Application mockClient = new Application();
        mockClient.setId(applicationId);
        mockClient.setName("client-name");
        mockClient.setDomain("wrong-domain");

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.APPLICATION))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockClient)).when(applicationService).findById(applicationId);

        final Response response = target("domains").path(domainId).path("applications").path(applicationId).request().get();
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldUpdateApplication() {

        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application mockApplication = buildApplicationMock();
        PatchApplication patchApplication = new PatchApplication();
        patchApplication.setDescription(Optional.of("New description"));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.APPLICATION))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(mockApplication)).when(applicationService).patch(eq(domainId), eq(mockApplication.getId()), any(PatchApplication.class), any(User.class));

        // heck all data are returned when having all permissions.
        final Response response = put(target("domains").path(domainId).path("applications").path(mockApplication.getId()), patchApplication);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Application application = readEntity(response, Application.class);
        assertEquals(mockApplication.getId(), application.getId());
        assertEquals(mockApplication.getName(), application.getName());
        assertEquals(mockApplication.getDomain(), application.getDomain());
        assertEquals(mockApplication.getType(), application.getType());
        assertEquals(mockApplication.getDescription(), application.getDescription());
        assertEquals(mockApplication.isEnabled(), application.isEnabled());
        assertEquals(mockApplication.isTemplate(), application.isTemplate());
        assertEquals(mockApplication.getFactors(), application.getFactors());
        assertEquals(mockApplication.getCreatedAt(), application.getCreatedAt());
        assertEquals(mockApplication.getUpdatedAt(), application.getUpdatedAt());
        assertEquals(mockApplication.getIdentities(), application.getIdentities());
        assertEquals(mockApplication.getCertificate(), application.getCertificate());
        assertNotNull(application.getSettings());
        assertNotNull(application.getSettings().getAdvanced());
        assertNotNull(application.getSettings().getAccount());
        assertNotNull(application.getSettings().getOauth());
    }

    @Test
    public void shouldUpdateFilteredApplication() {

        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application mockApplication = buildApplicationMock();
        PatchApplication patchApplication = new PatchApplication();
        patchApplication.setDescription(Optional.of("New description"));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.of(Permission.APPLICATION, Acl.READ))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString()); // only application read permission
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(mockApplication)).when(applicationService).patch(eq(domainId), eq(mockApplication.getId()), any(PatchApplication.class), any(User.class));

        // Check all data are returned when having all permissions.
        final Response response = put(target("domains").path(domainId).path("applications").path(mockApplication.getId()), patchApplication);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Application application = readEntity(response, Application.class);
        assertEquals(mockApplication.getId(), application.getId());
        assertEquals(mockApplication.getName(), application.getName());
        assertEquals(mockApplication.getDomain(), application.getDomain());
        assertEquals(mockApplication.getType(), application.getType());
        assertEquals(mockApplication.getDescription(), application.getDescription());
        assertEquals(mockApplication.isEnabled(), application.isEnabled());
        assertEquals(mockApplication.isTemplate(), application.isTemplate());
        assertNull(application.getFactors());
        assertEquals(mockApplication.getCreatedAt(), application.getCreatedAt());
        assertEquals(mockApplication.getUpdatedAt(), application.getUpdatedAt());
        assertNull(application.getIdentities());
        assertNull(application.getCertificate());
        assertNotNull(application.getSettings());
        assertNull(application.getSettings().getAccount());
        assertNull(application.getSettings().getOauth());
    }

    @Test
    public void shouldUpdateApplication_domainNotFound() {

        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application mockApplication = buildApplicationMock();
        PatchApplication patchApplication = new PatchApplication();
        patchApplication.setDescription(Optional.of("New description"));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.of(Permission.APPLICATION, Acl.READ))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString()); // only application read permission
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = put(target("domains").path(domainId).path("applications").path(mockApplication.getId()), patchApplication);
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldUpdateApplication_forbidden() {

        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Application mockApplication = buildApplicationMock();
        PatchApplication patchApplication = new PatchApplication();
        patchApplication.setDescription(Optional.of("New description"));

        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);

        final Response response = put(target("domains").path(domainId).path("applications").path(mockApplication.getId()), patchApplication);
        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldUpdateApplication_badRequest() {

        // Empty patch should result in 400 bad request.
        PatchApplication patchApplication = new PatchApplication();

        final Response response = put(target("domains").path("domain-id").path("applications").path("application-id"), patchApplication);
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldRenewClientSecret() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";
        final Application mockClient = new Application();
        mockClient.setId(clientId);
        mockClient.setName("client-name");
        mockClient.setDomain(domainId);

        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.APPLICATION))).when(permissionService).findAllPermissions(any(User.class), eq(ReferenceType.APPLICATION), anyString());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(mockClient)).when(applicationService).renewClientSecret(eq(domainId), eq(clientId), any());

        final Response response = target("domains")
                .path(domainId)
                .path("applications")
                .path(clientId)
                .path("secret/_renew")
                .request()
                .post(null);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldRenewClientSecret_appNotFound() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final String clientId = "client-id";
        final Application mockClient = new Application();
        mockClient.setId(clientId);
        mockClient.setName("client-name");
        mockClient.setDomain(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.error(new ApplicationNotFoundException(clientId))).when(applicationService).renewClientSecret(eq(domainId), eq(clientId), any());

        final Response response = target("domains")
                .path(domainId)
                .path("applications")
                .path(clientId)
                .path("secret/_renew")
                .request()
                .post(null);
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    private Application buildApplicationMock() {

        Application mockApplication = new Application();
        mockApplication.setId("client-id");
        mockApplication.setName("client-name");
        mockApplication.setDomain("domain-id");
        mockApplication.setType(ApplicationType.SERVICE);
        mockApplication.setDescription("description");
        mockApplication.setEnabled(true);
        mockApplication.setTemplate(true);
        mockApplication.setFactors(Collections.singleton("factor"));
        mockApplication.setCreatedAt(new Date());
        mockApplication.setUpdatedAt(new Date());
        mockApplication.setIdentities(Collections.singleton("identity"));
        mockApplication.setCertificate("certificate");

        ApplicationSettings filteredApplicationSettings = new ApplicationSettings();
        filteredApplicationSettings.setAdvanced(new ApplicationAdvancedSettings());
        filteredApplicationSettings.setAccount(new AccountSettings());
        filteredApplicationSettings.setOauth(new ApplicationOAuthSettings());

        mockApplication.setSettings(filteredApplicationSettings);
        mockApplication.setMetadata(Collections.singletonMap("key", "value"));

        return mockApplication;
    }
}
