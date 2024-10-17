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


import com.fasterxml.jackson.core.JsonProcessingException;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.exception.PasswordPolicyNotFoundException;
import io.gravitee.am.service.model.UpdatePasswordPolicy;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordPolicyResourceTest extends JerseySpringTest {
    private static final String DOMAIN_ID = UUID.randomUUID().toString();
    private static final String POLICY_ID = UUID.randomUUID().toString();

    protected Domain domain;

    @BeforeEach
    public void init() {
        reset(passwordPolicyService);
        this.domain = mock(Domain.class);
        when(domain.getId()).thenReturn(DOMAIN_ID);
        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
    }

    @Test
    public void shouldNotRead_NotPermitted() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .path(POLICY_ID)
                .request().get();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(passwordPolicyService, never()).findByReferenceAndId(any(), any(), any());
    }

    @Test
    public void getShouldReturn_NotFound() {
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        when(passwordPolicyService.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.empty());

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .path(POLICY_ID)
                .request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(passwordPolicyService).findByReferenceAndId(any(), any(), any());
    }

    @Test
    public void shouldRead() {
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        when(passwordPolicyService.findByReferenceAndId(any(), any(), any())).thenReturn(Maybe.just(new PasswordPolicy()));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .path(POLICY_ID)
                .request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        verify(passwordPolicyService).findByReferenceAndId(any(), any(), any());
    }

    @Test
    public void shouldNotUpdate_NotPermitted() {
        var updatePasswordPolicy = new UpdatePasswordPolicy();
        updatePasswordPolicy.setName("name");
        updatePasswordPolicy.setExcludePasswordsInDictionary(true);
        updatePasswordPolicy.setMaxLength(34);

        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .path(POLICY_ID)
                .request().put(Entity.json(updatePasswordPolicy));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(passwordPolicyService, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldUpdate_Policy() throws JsonProcessingException {
        var updatePasswordPolicy = new UpdatePasswordPolicy();
        updatePasswordPolicy.setName("name");
        updatePasswordPolicy.setExcludePasswordsInDictionary(true);
        updatePasswordPolicy.setMaxLength(34);

        var policy = new PasswordPolicy();
        policy.setId(POLICY_ID);
        policy.setName("name");
        policy.setMaxLength(34);
        policy.setReferenceId(DOMAIN_ID);
        policy.setExcludePasswordsInDictionary(true);
        policy.setReferenceType(ReferenceType.DOMAIN);

        doReturn(Single.just(policy)).when(passwordPolicyService).update(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(POLICY_ID), any(UpdatePasswordPolicy.class), any());
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .path(POLICY_ID)
                .request().put(Entity.json(updatePasswordPolicy));

        var result = objectMapper.readValue(response.readEntity(String.class), PasswordPolicy.class);
        assertEquals(result.getId(), policy.getId());
        assertEquals(result.getName(), policy.getName());
        assertEquals(result.getMaxLength(), policy.getMaxLength());
        assertEquals(result.getReferenceId(), policy.getReferenceId());
        assertEquals(result.getReferenceType(), policy.getReferenceType());
        assertEquals(result.getExcludePasswordsInDictionary(), policy.getExcludePasswordsInDictionary());

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        verify(passwordPolicyService).update(any(), any(), any(), any(), any());
    }

    @Test
    public void updateShould_Propagate_Exception() {
        var updatePasswordPolicy = new UpdatePasswordPolicy();
        updatePasswordPolicy.setName("name");
        updatePasswordPolicy.setExcludePasswordsInDictionary(true);
        updatePasswordPolicy.setMaxLength(34);

        doReturn(Single.error(new PasswordPolicyNotFoundException(POLICY_ID))).when(passwordPolicyService).update(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(POLICY_ID), any(UpdatePasswordPolicy.class), any());
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .path(POLICY_ID)
                .request().put(Entity.json(updatePasswordPolicy));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldSetDefault() throws JsonProcessingException {
        var updatePasswordPolicy = new UpdatePasswordPolicy();
        updatePasswordPolicy.setName("name");
        updatePasswordPolicy.setExcludePasswordsInDictionary(true);
        updatePasswordPolicy.setMaxLength(34);

        var policy = new PasswordPolicy();
        policy.setId(POLICY_ID);
        policy.setName("name");
        policy.setMaxLength(34);
        policy.setReferenceId(DOMAIN_ID);
        policy.setExcludePasswordsInDictionary(true);
        policy.setReferenceType(ReferenceType.DOMAIN);
        policy.setDefaultPolicy(Boolean.TRUE);

        doReturn(Single.just(policy)).when(passwordPolicyService).setDefaultPasswordPolicy(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(POLICY_ID), any());
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .path(POLICY_ID)
                .path("default")
                .request().post(Entity.json(null));

        var result = objectMapper.readValue(response.readEntity(String.class), PasswordPolicy.class);
        assertEquals(result.getId(), policy.getId());
        assertEquals(result.getName(), policy.getName());
        assertEquals(result.getMaxLength(), policy.getMaxLength());
        assertEquals(result.getReferenceId(), policy.getReferenceId());
        assertEquals(result.getReferenceType(), policy.getReferenceType());
        assertEquals(result.getExcludePasswordsInDictionary(), policy.getExcludePasswordsInDictionary());
        assertEquals(result.getDefaultPolicy(), policy.getDefaultPolicy());

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        verify(passwordPolicyService).setDefaultPasswordPolicy(any(), any(), any(), any());
    }

    @Test
    public void shouldNotDelete_NotPermitted() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .path(POLICY_ID)
                .request().delete();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(passwordPolicyService, never()).deleteAndUpdateIdp(any(), any(), any(), any());
    }

    @Test
    public void shouldDelete_Policy() {
        doReturn(Completable.complete()).when(passwordPolicyService).deleteAndUpdateIdp(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(POLICY_ID), any());
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .path(POLICY_ID)
                .request().delete();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void deleteShould_Propagate_Exception() {
        doReturn(Completable.error(new TechnicalException())).when(passwordPolicyService).deleteAndUpdateIdp(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(POLICY_ID), any());
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .path(POLICY_ID)
                .request().delete();

        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }
}
