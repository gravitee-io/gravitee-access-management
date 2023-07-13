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

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.am.service.model.NewMembership;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MembersResourceTest extends JerseySpringTest {

    @Test
    public void shouldNotAddMember_organizationNotFound() {

        final String organizationId = "orga-1";

        doReturn(Single.error(new OrganizationNotFoundException(organizationId))).when(organizationService).findById(organizationId);

        NewMembership newMembership = new NewMembership();
        newMembership.setMemberId("member#1");
        newMembership.setMemberType(MemberType.USER);
        newMembership.setRole("role#1");

        final Response response = target("/organizations")
                .path(organizationId)
                .path("members")
                .request()
                .post(Entity.json(newMembership));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldAddMember() {
        Organization organization = new Organization();
        organization.setId(Organization.DEFAULT);

        Membership membership = new Membership();
        membership.setId("membership-1");

        doReturn(Single.just(organization)).when(organizationService).findById(organization.getId());
        doReturn(Single.just(membership)).when(membershipService).addOrUpdate(eq(organization.getId()), any(Membership.class), any(io.gravitee.am.identityprovider.api.User.class));

        NewMembership newMembership = new NewMembership();
        newMembership.setMemberId("member#1");
        newMembership.setMemberType(MemberType.USER);
        newMembership.setRole("role#1");

        final Response response = target("organizations")
                .path(organization.getId())
                .path("members")
                .request()
                .post(Entity.json(newMembership));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }

    @Test
    public void shouldNotAddMember_invalidInput() {
        Organization organization = new Organization();
        organization.setId(Organization.DEFAULT);

        Membership membership = new Membership();
        membership.setId("membership-1");

        doReturn(Single.just(organization)).when(organizationService).findById(organization.getId());
        doReturn(Single.just(membership)).when(membershipService).addOrUpdate(eq(organization.getId()), any(Membership.class), any(io.gravitee.am.identityprovider.api.User.class));

        NewMembership newMembership = new NewMembership(); // invalid input.

        final Response response = target("organizations")
                .path(organization.getId())
                .path("members")
                .request()
                .post(Entity.json(newMembership));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldGetMembers() {
        Organization organization = new Organization();
        organization.setId("orga#1");

        Membership membership = new Membership();
        membership.setId("membership#1");

        doReturn(Single.just(organization)).when(organizationService).findById(organization.getId());
        doReturn(Flowable.just(Arrays.asList(membership))).when(membershipService).findByReference(organization.getId(), ReferenceType.ORGANIZATION);
        doReturn(Single.just(new HashMap<>())).when(membershipService).getMetadata(anyList());

        final Response response = target("organizations")
                .path(organization.getId())
                .path("members")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldGetMembers_organizationNotFound() {
        Organization organization = new Organization();
        organization.setId("orga#1");

        Membership membership = new Membership();
        membership.setId("membership#1");

        doReturn(Single.error(new OrganizationNotFoundException(organization.getId()))).when(organizationService).findById(organization.getId());

        final Response response = target("organizations")
                .path(organization.getId())
                .path("members")
                .request()
                .get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }
}