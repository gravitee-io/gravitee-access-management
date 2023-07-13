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
import io.gravitee.am.model.Organization;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MemberResourceTest extends JerseySpringTest {

    @Test
    public void shouldNotDeleteMember_organizationNotFound() {

        final String organizationId = "orga-1";

        doReturn(Single.error(new OrganizationNotFoundException(organizationId))).when(organizationService).findById(organizationId);

        final Response response = target("/organizations")
                .path(organizationId)
                .path("members")
                .path("membership-1")
                .request()
                .delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldDeleteMember() {

        String membershipId = "membership-1";
        Organization organization = new Organization();
        organization.setId(Organization.DEFAULT);

        doReturn(Single.just(organization)).when(organizationService).findById(organization.getId());
        doReturn(Completable.complete()).when(membershipService).delete(eq(membershipId), any(io.gravitee.am.identityprovider.api.User.class));

        final Response response = target("/organizations")
                .path(organization.getId())
                .path("members")
                .path(membershipId)
                .request()
                .delete();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }
}