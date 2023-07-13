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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.core.Response;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupMemberResourceTest extends JerseySpringTest {

    @Test
    public void shouldNotAddMember_domainNotFound() {
        final String domainId = "domain-1";

        final Group mockGroup = new Group();
        mockGroup.setId("group-id-1");

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("groups")
                .path(mockGroup.getId())
                .path("members")
                .path("member-1")
                .request()
                .post(null);

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotAddMember_groupNotFound() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Group mockGroup = new Group();
        mockGroup.setId("group-id-1");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(groupService).findById(mockGroup.getId());

        final Response response = target("domains")
                .path(domainId)
                .path("groups")
                .path(mockGroup.getId())
                .path("members")
                .path("member-1")
                .request()
                .post(null);

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotAddMember_userNotFound() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Group mockGroup = new Group();
        mockGroup.setId("group-id-1");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockGroup)).when(groupService).findById(mockGroup.getId());
        doReturn(Maybe.empty()).when(userService).findById("member-1");

        final Response response = target("domains")
                .path(domainId)
                .path("groups")
                .path(mockGroup.getId())
                .path("members")
                .path("member-1")
                .request()
                .post(null);

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotAddMember_memberAlreadyExists() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Group mockGroup = new Group();
        mockGroup.setId("group-id-1");
        mockGroup.setMembers(Arrays.asList("member-1"));

        final User mockUser = new User();
        mockUser.setId("member-1");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockGroup)).when(groupService).findById(mockGroup.getId());
        doReturn(Maybe.just(mockUser)).when(userService).findById(mockUser.getId());

        final Response response = target("domains")
                .path(domainId)
                .path("groups")
                .path(mockGroup.getId())
                .path("members")
                .path(mockUser.getId())
                .request()
                .post(null);

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldAddMember() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Group mockGroup = new Group();
        mockGroup.setId("group-id-1");

        final User mockUser = new User();
        mockUser.setId("member-1");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockGroup)).when(groupService).findById(mockGroup.getId());
        doReturn(Single.just(mockGroup)).when(groupService).update(eq(mockDomain.getId()), eq(mockGroup.getId()), any(), any());
        doReturn(Maybe.just(mockUser)).when(userService).findById(mockUser.getId());

        final Response response = target("domains")
                .path(domainId)
                .path("groups")
                .path(mockGroup.getId())
                .path("members")
                .path(mockUser.getId())
                .request()
                .post(null);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldNotRemoveMember_domainNotFound() {
        final String domainId = "domain-1";

        final Group mockGroup = new Group();
        mockGroup.setId("group-id-1");

        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("groups")
                .path(mockGroup.getId())
                .path("members")
                .path("member-1")
                .request()
                .delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotRemoveMember_groupNotFound() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Group mockGroup = new Group();
        mockGroup.setId("group-id-1");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(groupService).findById(mockGroup.getId());

        final Response response = target("domains")
                .path(domainId)
                .path("groups")
                .path(mockGroup.getId())
                .path("members")
                .path("member-1")
                .request()
                .delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotRemoveMember_userNotFound() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Group mockGroup = new Group();
        mockGroup.setId("group-id-1");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockGroup)).when(groupService).findById(mockGroup.getId());
        doReturn(Maybe.empty()).when(userService).findById("member-1");

        final Response response = target("domains")
                .path(domainId)
                .path("groups")
                .path(mockGroup.getId())
                .path("members")
                .path("member-1")
                .request()
                .delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldRemoveMember_memberNotFound() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Group mockGroup = new Group();
        mockGroup.setId("group-id-1");

        final User mockUser = new User();
        mockUser.setId("member-1");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockGroup)).when(groupService).findById(mockGroup.getId());
        doReturn(Single.just(mockGroup)).when(groupService).update(eq(mockDomain.getId()), eq(mockGroup.getId()), any(), any());
        doReturn(Maybe.just(mockUser)).when(userService).findById(mockUser.getId());

        final Response response = target("domains")
                .path(domainId)
                .path("groups")
                .path(mockGroup.getId())
                .path("members")
                .path(mockUser.getId())
                .request()
                .delete();

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldRemoveMember() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Group mockGroup = new Group();
        mockGroup.setId("group-id-1");
        mockGroup.setMembers(Arrays.asList("member-1"));

        final User mockUser = new User();
        mockUser.setId("member-1");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(mockGroup)).when(groupService).findById(mockGroup.getId());
        doReturn(Single.just(mockGroup)).when(groupService).update(eq(mockDomain.getId()), eq(mockGroup.getId()), any(), any());
        doReturn(Maybe.just(mockUser)).when(userService).findById(mockUser.getId());

        final Response response = target("domains")
                .path(domainId)
                .path("groups")
                .path(mockGroup.getId())
                .path("members")
                .path(mockUser.getId())
                .request()
                .delete();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }
}
