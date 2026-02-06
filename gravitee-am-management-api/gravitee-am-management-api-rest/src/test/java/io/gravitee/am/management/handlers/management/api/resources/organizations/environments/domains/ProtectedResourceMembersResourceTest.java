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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.service.model.NewMembership;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

public class ProtectedResourceMembersResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String PROTECTED_RESOURCE_ID = "protected-resource-1";

    @Test
    public void shouldAddMember() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId(PROTECTED_RESOURCE_ID);

        Membership membership = new Membership();
        membership.setId("membership-1");

        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(protectedResource)).when(protectedResourceService).findById(PROTECTED_RESOURCE_ID);
        doReturn(Single.just(membership))
                .when(membershipService)
                .addOrUpdate(eq(Organization.DEFAULT), any(Membership.class), any(User.class));
        doReturn(Completable.complete())
                .when(membershipService)
                .addDomainUserRoleIfNecessary(eq(Organization.DEFAULT), eq(Environment.DEFAULT), eq(DOMAIN_ID), any(NewMembership.class), any(User.class));

        NewMembership newMembership = new NewMembership();
        newMembership.setMemberId("member#1");
        newMembership.setMemberType(MemberType.USER);
        newMembership.setRole("role#1");

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("members")
                .request()
                .post(Entity.json(newMembership));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }

    @Test
    public void shouldNotAddMember_invalidInput() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId(PROTECTED_RESOURCE_ID);

        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(protectedResource)).when(protectedResourceService).findById(PROTECTED_RESOURCE_ID);

        NewMembership newMembership = new NewMembership();

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("members")
                .request()
                .post(Entity.json(newMembership));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldNotAddMember_domainNotFound() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        NewMembership newMembership = new NewMembership();
        newMembership.setMemberId("member#1");
        newMembership.setMemberType(MemberType.USER);
        newMembership.setRole("role#1");

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("members")
                .request()
                .post(Entity.json(newMembership));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotAddMember_protectedResourceNotFound() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);

        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(protectedResourceService).findById(PROTECTED_RESOURCE_ID);

        NewMembership newMembership = new NewMembership();
        newMembership.setMemberId("member#1");
        newMembership.setMemberType(MemberType.USER);
        newMembership.setRole("role#1");

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("members")
                .request()
                .post(Entity.json(newMembership));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetMembers() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId(PROTECTED_RESOURCE_ID);

        Membership membership = new Membership();
        membership.setId("membership-1");

        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(protectedResource)).when(protectedResourceService).findById(PROTECTED_RESOURCE_ID);
        doReturn(Flowable.just(membership)).when(membershipService).findByReference(PROTECTED_RESOURCE_ID, ReferenceType.PROTECTED_RESOURCE);
        doReturn(Single.just(new HashMap<>())).when(membershipService).getMetadata(anyList());

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("members")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldGetMembers_domainNotFound() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("members")
                .request()
                .get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetMembers_protectedResourceNotFound() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);

        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(protectedResourceService).findById(PROTECTED_RESOURCE_ID);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("members")
                .request()
                .get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetMemberPermissions() {
        doReturn(Single.just(Collections.emptyMap()))
                .when(permissionService)
                .findAllPermissions(any(User.class), eq(ReferenceType.PROTECTED_RESOURCE), eq(PROTECTED_RESOURCE_ID));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("members")
                .path("permissions")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }
}
