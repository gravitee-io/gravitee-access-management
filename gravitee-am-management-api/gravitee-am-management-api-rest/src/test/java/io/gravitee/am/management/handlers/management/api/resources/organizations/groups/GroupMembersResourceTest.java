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
package io.gravitee.am.management.handlers.management.api.resources.organizations.groups;

import com.fasterxml.jackson.databind.JsonNode;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

public class GroupMembersResourceTest extends JerseySpringTest {

    private static final String ORGANIZATION_ID = "DEFAULT";
    private static final String GROUP_ID = "group-id";

    @Autowired
    @Qualifier("identityProviderService")
    private IdentityProviderService idpService;

    @Test
    public void shouldListMembersAsUserEntities() {
        User member = new User();
        member.setId("user-id");
        member.setUsername("alice");
        member.setPassword("$2a$10$hash");
        member.setSource("gravitee");
        member.setReferenceType(ReferenceType.ORGANIZATION);
        member.setReferenceId(ORGANIZATION_ID);

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId("gravitee");
        identityProvider.setName("Gravitee");

        doReturn(Single.just(new Page<>(List.of(member), 0, 1))).when(organizationGroupService).findMembers(eq(ORGANIZATION_ID), eq(GROUP_ID), anyInt(), anyInt());
        doReturn(Maybe.just(identityProvider)).when(idpService).findById("gravitee");

        final Response response = target("organizations")
                .path(ORGANIZATION_ID)
                .path("groups")
                .path(GROUP_ID)
                .path("members")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        JsonNode returnedMember = readEntity(response, JsonNode.class).get("data").get(0);
        assertEquals("alice", returnedMember.get("username").asText());
        assertEquals("Gravitee", returnedMember.get("source").asText());
        assertFalse(returnedMember.has("password"));
    }
}
