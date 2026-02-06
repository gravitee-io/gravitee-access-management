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
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

public class ProtectedResourceMemberResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String PROTECTED_RESOURCE_ID = "protected-resource-1";

    @Test
    public void shouldDeleteMember() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId(PROTECTED_RESOURCE_ID);

        String membershipId = "membership-1";

        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(protectedResource)).when(protectedResourceService).findById(PROTECTED_RESOURCE_ID);
        doReturn(Completable.complete()).when(membershipService).delete(argThat(ref -> ref.id().equals(PROTECTED_RESOURCE_ID)), eq(membershipId), any(User.class));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("members")
                .path(membershipId)
                .request()
                .delete();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldNotDeleteMember_domainNotFound() {
        String membershipId = "membership-1";
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("members")
                .path(membershipId)
                .request()
                .delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotDeleteMember_protectedResourceNotFound() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);

        String membershipId = "membership-1";

        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(protectedResourceService).findById(PROTECTED_RESOURCE_ID);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("members")
                .path(membershipId)
                .request()
                .delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }
}
