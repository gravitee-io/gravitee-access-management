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
import io.gravitee.am.model.*;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainResourceTest extends JerseySpringTest {


    public static final String ORGANIZATION_ID = "orga-1";
    public static final String ENTRYPOINT_ID1 = "entrypoint-1";
    public static final String ENVIRONMENT_ID = "env-1";
    public static final String DOMAIN_ID = "domain-1";
    public static final String TAG_ID2 = "tag#2";
    public static final String TAG_ID1 = "tag#1";
    public static final String ENTRYPOINT_ID2 = "entrypoint-2";
    public static final String ENTRYPOINT_ID_DEFAULT = "DEFAULT";

    @Test
    public void shouldGetDomain() {

        final Domain mockDomain = buildDomainMock();

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString());
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(mockDomain.getId());

        final Response response = target("domains").path(mockDomain.getId()).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        assertEquals(mockDomain.getId(), domain.getId());
        assertEquals(mockDomain.getName(), domain.getName());
        assertEquals(mockDomain.getDescription(), domain.getDescription());
        assertEquals(mockDomain.isEnabled(), domain.isEnabled());
        assertEquals(mockDomain.getCreatedAt(), domain.getCreatedAt());
        assertEquals(mockDomain.getUpdatedAt(), domain.getUpdatedAt());
        assertEquals(mockDomain.getPath(), domain.getPath());
        assertEquals(mockDomain.getReferenceType(), domain.getReferenceType());
        assertEquals(mockDomain.getReferenceId(), domain.getReferenceId());
        assertNotNull(domain.getOidc());
        assertNotNull(domain.getScim());
        assertNotNull(domain.getLoginSettings());
        assertNotNull(domain.getAccountSettings());
        assertEquals(mockDomain.getTags(), domain.getTags());
    }

    @Test
    public void shouldGetFilteredDomain() {

        final Domain mockDomain = buildDomainMock();

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.of(Permission.DOMAIN, Acl.READ))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString()); // only domain read permission
        doReturn(Maybe.just(mockDomain)).when(domainService).findById(mockDomain.getId());

        final Response response = target("domains").path(mockDomain.getId()).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        assertEquals(mockDomain.getId(), domain.getId());
        assertEquals(mockDomain.getName(), domain.getName());
        assertEquals(mockDomain.getDescription(), domain.getDescription());
        assertEquals(mockDomain.isEnabled(), domain.isEnabled());
        assertEquals(mockDomain.getCreatedAt(), domain.getCreatedAt());
        assertEquals(mockDomain.getUpdatedAt(), domain.getUpdatedAt());
        assertEquals(mockDomain.getPath(), domain.getPath());
        assertEquals(mockDomain.getReferenceType(), domain.getReferenceType());
        assertEquals(mockDomain.getReferenceId(), domain.getReferenceId());
        assertNull(domain.getOidc());
        assertNull(domain.getScim());
        assertNull(domain.getLoginSettings());
        assertNull(domain.getAccountSettings());
        assertNull(domain.getTags());
    }

    @Test
    public void shouldGetDomain_notFound() {
        final String domainId = "domain-id";
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetDomain_forbidden() {
        final String domainId = "domain-id";

        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).request().get();
        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldGetDomain_technicalManagementException() {
        final String domainId = "domain-id";
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(domainService).findById(domainId);

        final Response response = target("domains").path(domainId).request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldUpdateDomain() {

        Domain mockDomain = buildDomainMock();
        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setDescription(Optional.of("New description"));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString());
        doReturn(Single.just(mockDomain)).when(domainService).patch(eq(mockDomain.getId()), any(PatchDomain.class), any(User.class));

        final Response response = put(target("domains").path(mockDomain.getId()), patchDomain);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        assertEquals(mockDomain.getId(), domain.getId());
        assertEquals(mockDomain.getName(), domain.getName());
        assertEquals(mockDomain.getDescription(), domain.getDescription());
        assertEquals(mockDomain.isEnabled(), domain.isEnabled());
        assertEquals(mockDomain.getCreatedAt(), domain.getCreatedAt());
        assertEquals(mockDomain.getUpdatedAt(), domain.getUpdatedAt());
        assertEquals(mockDomain.getPath(), domain.getPath());
        assertEquals(mockDomain.getReferenceType(), domain.getReferenceType());
        assertEquals(mockDomain.getReferenceId(), domain.getReferenceId());
        assertNotNull(domain.getOidc());
        assertNotNull(domain.getScim());
        assertNotNull(domain.getLoginSettings());
        assertNotNull(domain.getAccountSettings());
        assertEquals(mockDomain.getTags(), domain.getTags());
    }

    @Test
    public void shouldUpdateDomainVHosts() {

        ArrayList<VirtualHost> vhosts = new ArrayList<>();
        VirtualHost vhost = new VirtualHost();
        vhost.setHost("valid.host.gravitee.io");
        vhost.setPath("/validVhostPath");
        vhosts.add(vhost);

        Domain mockDomain = buildDomainMock();
        mockDomain.setVhostMode(true);
        mockDomain.setVhosts(vhosts);
        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setDescription(Optional.of("New description"));
        patchDomain.setVhostMode(Optional.of(true));
        patchDomain.setVhosts(Optional.of(vhosts));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.allPermissionAcls(ReferenceType.DOMAIN))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString());
        doReturn(Single.just(mockDomain)).when(domainService).patch(eq(mockDomain.getId()), any(PatchDomain.class), any(User.class));

        final Response response = put(target("domains").path(mockDomain.getId()), patchDomain);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        assertEquals(mockDomain.getId(), domain.getId());
        assertEquals(mockDomain.getName(), domain.getName());
        assertEquals(mockDomain.getDescription(), domain.getDescription());
        assertEquals(mockDomain.isEnabled(), domain.isEnabled());
        assertEquals(mockDomain.getCreatedAt(), domain.getCreatedAt());
        assertEquals(mockDomain.getUpdatedAt(), domain.getUpdatedAt());
        assertEquals(mockDomain.getPath(), domain.getPath());
        assertEquals(mockDomain.getReferenceType(), domain.getReferenceType());
        assertEquals(mockDomain.getReferenceId(), domain.getReferenceId());
        assertEquals(mockDomain.isVhostMode(), domain.isVhostMode());
        assertNotNull(domain.getVhosts());
        assertEquals(1, domain.getVhosts().size());
        assertNotNull(domain.getOidc());
        assertNotNull(domain.getScim());
        assertNotNull(domain.getLoginSettings());
        assertNotNull(domain.getAccountSettings());
        assertEquals(mockDomain.getTags(), domain.getTags());
    }

    @Test
    public void shouldUpdateFilteredDomain() {

        Domain mockDomain = buildDomainMock();
        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setDescription(Optional.of("New description"));

        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(Permission.of(Permission.DOMAIN, Acl.READ))).when(permissionService).findAllPermissions(any(User.class), any(ReferenceType.class), anyString()); // only domain read permission
        doReturn(Single.just(mockDomain)).when(domainService).patch(eq(mockDomain.getId()), any(PatchDomain.class), any(User.class));

        final Response response = put(target("domains").path(mockDomain.getId()), patchDomain);
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Domain domain = readEntity(response, Domain.class);
        assertEquals(mockDomain.getId(), domain.getId());
        assertEquals(mockDomain.getName(), domain.getName());
        assertEquals(mockDomain.getDescription(), domain.getDescription());
        assertEquals(mockDomain.isEnabled(), domain.isEnabled());
        assertEquals(mockDomain.getCreatedAt(), domain.getCreatedAt());
        assertEquals(mockDomain.getUpdatedAt(), domain.getUpdatedAt());
        assertEquals(mockDomain.getPath(), domain.getPath());
        assertEquals(mockDomain.getReferenceType(), domain.getReferenceType());
        assertEquals(mockDomain.getReferenceId(), domain.getReferenceId());
        assertNull(domain.getOidc());
        assertNull(domain.getScim());
        assertNull(domain.getLoginSettings());
        assertNull(domain.getAccountSettings());
        assertNull(domain.getTags());
    }

    @Test
    public void shouldUpdateDomain_forbidden() {
        final String domainId = "domain-id";

        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setDescription(Optional.of("New description"));

        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
        doReturn(Single.just(new Domain())).when(domainService).patch(anyString(), any(PatchDomain.class), any(User.class));

        final Response response = put(target("domains").path(domainId), patchDomain);
        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
    }

    @Test
    public void shouldUpdateDomain_badRequest() {

        // Empty patch should result in 400 bad request.
        PatchDomain patchDomain = new PatchDomain();

        final Response response = put(target("domains").path("domain-id"), patchDomain);
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldGetEntrypoints_entrypoint1() {

        final Entrypoint entrypoint = new Entrypoint();
        entrypoint.setId(ENTRYPOINT_ID1);
        entrypoint.setName("entrypoint-1-name");
        entrypoint.setTags(Arrays.asList(TAG_ID2));
        entrypoint.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint entrypoint2 = new Entrypoint();
        entrypoint2.setId(ENTRYPOINT_ID2);
        entrypoint2.setName("entrypoint-2-name");
        entrypoint2.setTags(Collections.emptyList());
        entrypoint2.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint defaultEntrypoint = new Entrypoint();
        defaultEntrypoint.setId(ENTRYPOINT_ID_DEFAULT);
        defaultEntrypoint.setName("Default");
        defaultEntrypoint.setTags(Collections.emptyList());
        defaultEntrypoint.setDefaultEntrypoint(true);
        defaultEntrypoint.setOrganizationId(ORGANIZATION_ID);

        Domain mockDomain = new Domain();
        mockDomain.setId(DOMAIN_ID);
        mockDomain.setTags(new HashSet<>(Arrays.asList(TAG_ID1, TAG_ID2)));

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(mockDomain.getId());
        doReturn(Flowable.just(entrypoint, entrypoint2, defaultEntrypoint)).when(entrypointService).findAll(ORGANIZATION_ID);

        final Response response = target("organizations")
                .path(ORGANIZATION_ID)
                .path("environments")
                .path(ENVIRONMENT_ID)
                .path("domains")
                .path(DOMAIN_ID)
                .path("entrypoints").request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        List<Entrypoint> entrypoints = readListEntity(response, Entrypoint.class);

        assertEquals(1, entrypoints.size());
        assertTrue(entrypoints.stream().anyMatch(e -> e.getId().equals(ENTRYPOINT_ID1)));
    }

    @Test
    public void shouldGetEntrypoints_default() {

        final Entrypoint entrypoint = new Entrypoint();
        entrypoint.setId(ENTRYPOINT_ID1);
        entrypoint.setName("entrypoint-1-name");
        entrypoint.setTags(Collections.emptyList());
        entrypoint.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint entrypoint2 = new Entrypoint();
        entrypoint2.setId(ENTRYPOINT_ID2);
        entrypoint2.setName("entrypoint-2-name");
        entrypoint2.setTags(Collections.emptyList());
        entrypoint2.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint defaultEntrypoint = new Entrypoint();
        defaultEntrypoint.setId(ENTRYPOINT_ID_DEFAULT);
        defaultEntrypoint.setName("Default");
        defaultEntrypoint.setTags(Collections.emptyList());
        defaultEntrypoint.setDefaultEntrypoint(true);
        defaultEntrypoint.setOrganizationId(ORGANIZATION_ID);

        Domain mockDomain = new Domain();
        mockDomain.setId(DOMAIN_ID);
        mockDomain.setTags(new HashSet<>());

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(mockDomain.getId());
        doReturn(Flowable.just(entrypoint, entrypoint2, defaultEntrypoint)).when(entrypointService).findAll(ORGANIZATION_ID);

        final Response response = target("organizations")
                .path(ORGANIZATION_ID)
                .path("environments")
                .path(ENVIRONMENT_ID)
                .path("domains")
                .path(DOMAIN_ID)
                .path("entrypoints").request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        List<Entrypoint> entrypoints = readListEntity(response, Entrypoint.class);

        assertEquals(1, entrypoints.size());
        assertTrue(entrypoints.stream().anyMatch(e -> e.getId().equals(ENTRYPOINT_ID_DEFAULT)));
    }

    @Test
    public void shouldGetEntrypoints_Entrypoint1And2() {

        final Entrypoint entrypoint = new Entrypoint();
        entrypoint.setId(ENTRYPOINT_ID1);
        entrypoint.setName("entrypoint-1-name");
        entrypoint.setTags(Arrays.asList(TAG_ID1));
        entrypoint.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint entrypoint2 = new Entrypoint();
        entrypoint2.setId(ENTRYPOINT_ID2);
        entrypoint2.setName("entrypoint-2-name");
        entrypoint2.setTags(Arrays.asList(TAG_ID2));
        entrypoint2.setOrganizationId(ORGANIZATION_ID);

        final Entrypoint defaultEntrypoint = new Entrypoint();
        defaultEntrypoint.setId(ENTRYPOINT_ID_DEFAULT);
        defaultEntrypoint.setName("Default");
        defaultEntrypoint.setTags(Collections.emptyList());
        defaultEntrypoint.setDefaultEntrypoint(true);
        defaultEntrypoint.setOrganizationId(ORGANIZATION_ID);

        Domain mockDomain = new Domain();
        mockDomain.setId(DOMAIN_ID);
        mockDomain.setTags(new HashSet<>(Arrays.asList(TAG_ID1, TAG_ID2)));

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(mockDomain.getId());
        doReturn(Flowable.just(entrypoint, entrypoint2, defaultEntrypoint)).when(entrypointService).findAll(ORGANIZATION_ID);

        final Response response = target("organizations")
                .path(ORGANIZATION_ID)
                .path("environments")
                .path(ENVIRONMENT_ID)
                .path("domains")
                .path(DOMAIN_ID)
                .path("entrypoints").request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        List<Entrypoint> entrypoints = readListEntity(response, Entrypoint.class);

        assertEquals(2, entrypoints.size());
        assertTrue(entrypoints.stream().anyMatch(e -> e.getId().equals(ENTRYPOINT_ID1)));
        assertTrue(entrypoints.stream().anyMatch(e -> e.getId().equals(ENTRYPOINT_ID2)));
    }

    private Domain buildDomainMock() {
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
        mockDomain.setOidc(new OIDCSettings());
        mockDomain.setScim(new SCIMSettings());
        mockDomain.setLoginSettings(new LoginSettings());
        mockDomain.setAccountSettings(new AccountSettings());
        mockDomain.setTags(Collections.singleton("tag"));
        return mockDomain;
    }
}
