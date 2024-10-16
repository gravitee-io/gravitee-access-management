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
import io.gravitee.am.management.handlers.management.api.model.PasswordPolicyEntity;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.model.NewPasswordPolicy;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.HOURS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rafal PODLES (rafal.podles at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordPoliciesResourceTest extends JerseySpringTest {
    private static final String DOMAIN_ID = "domain-id";


    protected Domain domain;

    @BeforeEach
    public void init() {
        this.domain = mock(Domain.class);
        when(domain.getId()).thenReturn(DOMAIN_ID);
        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(true)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));
    }

    @Test
    public void shouldNotCreate_NotPermitted() {

        var newPasswordPolicy = new NewPasswordPolicy();
        newPasswordPolicy.setName("name");
        newPasswordPolicy.setExcludePasswordsInDictionary(true);
        newPasswordPolicy.setMaxLength(34);

        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .request().post(Entity.json(newPasswordPolicy));

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        assertNull(response.getHeaderString(HttpHeaders.LOCATION));
        verify(passwordPolicyService, never()).create(any(), any());
    }

    @Test
    public void shouldCreate_Policy() throws JsonProcessingException {

        var newPasswordPolicy = new NewPasswordPolicy();
        newPasswordPolicy.setName("name");
        newPasswordPolicy.setExcludePasswordsInDictionary(true);
        newPasswordPolicy.setMaxLength(34);

        var policy = new PasswordPolicy();
        policy.setName("name");
        policy.setMaxLength(34);
        policy.setReferenceId(DOMAIN_ID);
        policy.setId(UUID.randomUUID().toString());
        policy.setExcludePasswordsInDictionary(true);
        policy.setReferenceType(ReferenceType.DOMAIN);

        doReturn(Single.just(policy)).when(passwordPolicyService).create(any(PasswordPolicy.class), any());

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .request().post(Entity.json(newPasswordPolicy));

        var result = objectMapper.readValue(response.readEntity(String.class), PasswordPolicy.class);
        assertEquals(result.getId(), policy.getId());
        assertEquals(result.getName(), policy.getName());
        assertEquals(result.getMaxLength(), policy.getMaxLength());
        assertEquals(result.getReferenceId(), policy.getReferenceId());
        assertEquals(result.getReferenceType(), policy.getReferenceType());
        assertEquals(result.getExcludePasswordsInDictionary(), policy.getExcludePasswordsInDictionary());

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
        assertTrue(response.getHeaderString(HttpHeaders.LOCATION).endsWith("/password-policies/" + policy.getId()));
        verify(passwordPolicyService).create(any(), any());
    }

    @Test
    public void shouldNotCreate_MissingName() {

        var newPasswordPolicy = new NewPasswordPolicy();
        newPasswordPolicy.setExcludePasswordsInDictionary(true);
        newPasswordPolicy.setMaxLength(34);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .request().post(Entity.json(newPasswordPolicy));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
        assertNull(response.getHeaderString(HttpHeaders.LOCATION));
        verify(passwordPolicyService, never()).create(any(), any());
    }

    @Test
    public void shouldGetPasswordPolicies() {

        PasswordPolicy passwordPolicyNoDate = new PasswordPolicy();
        passwordPolicyNoDate.setId("policyIdNoDate");
        passwordPolicyNoDate.setName("policyNoDate");
        passwordPolicyNoDate.setDefaultPolicy(Boolean.TRUE);
        PasswordPolicy passwordPolicy1 = new PasswordPolicy();
        passwordPolicy1.setId("policyId1");
        passwordPolicy1.setName("policy1");
        passwordPolicy1.setDefaultPolicy(Boolean.TRUE);
        passwordPolicy1.setCreatedAt(new Date(Instant.now().toEpochMilli()));
        PasswordPolicy passwordPolicy2 = new PasswordPolicy();
        passwordPolicy2.setId("policyId2");
        passwordPolicy2.setName("policy2");
        passwordPolicy2.setCreatedAt(new Date(Instant.now().minus(1, HOURS).toEpochMilli()));
        passwordPolicy2.setDefaultPolicy(Boolean.FALSE);

        doReturn(Flowable.just(passwordPolicy1, passwordPolicyNoDate, passwordPolicy2)).when(passwordPolicyService).findByDomain(DOMAIN_ID);
        when(identityProviderService.findWithPasswordPolicy(any(), any(), any())).thenReturn(Flowable.empty());

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        List<PasswordPolicyEntity> policies = readListEntity(response, PasswordPolicyEntity.class);
        assertEquals(3, policies.size());
        assertEquals(passwordPolicy2.getId(), policies.get(0).getId());
        assertEquals(passwordPolicy1.getId(), policies.get(1).getId());
        assertEquals(passwordPolicyNoDate.getId(), policies.get(2).getId());
    }

    @Test
    public void shouldGetPasswordPoliciesReturnNoContent() {

        doReturn(Flowable.empty()).when(passwordPolicyService).findByDomain(DOMAIN_ID);
        when(identityProviderService.findWithPasswordPolicy(any(), any(), any())).thenReturn(Flowable.empty());

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("password-policies")
                .request()
                .get();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }
}
