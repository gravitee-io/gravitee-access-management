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
import io.gravitee.am.model.Tag;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewTag;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TagsResourceTest extends JerseySpringTest {

    public static final String ORGANIZATION_ID = "orga#1";

    @Test
    public void shouldGetTags() {
        final Tag mockRole = new Tag();
        mockRole.setId("role-1-id");
        mockRole.setName("role-1-name");
        mockRole.setOrganizationId("orga#1");

        final Tag mockRole2 = new Tag();
        mockRole2.setId("role-2-id");
        mockRole2.setName("role-2-name");
        mockRole2.setOrganizationId(ORGANIZATION_ID);

        doReturn(Flowable.just(mockRole, mockRole2)).when(tagService).findAll(anyString());

        final Response response = target("organizations")
                .path("DEFAULT")
                .path("tags").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<Tag> responseEntity = readEntity(response, List.class);
        assertEquals(2, responseEntity.size());
    }

    @Test
    public void shouldGetTags_technicalManagementException() {
        doReturn(Flowable.error(new TechnicalManagementException("error occurs"))).when(tagService).findAll(anyString());

        final Response response = target("organizations")
                .path("DEFAULT")
                .path("tags").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldCreate() {
        NewTag newTag = new NewTag();
        newTag.setName("tag-name");

        Tag tag = new Tag();
        tag.setId("tag-id");
        tag.setName("tag-name");

        doReturn(Single.just(tag)).when(tagService).create(any(), anyString(), any());

        final Response response = target("organizations")
                .path("DEFAULT")
                .path("tags")
                .request().post(Entity.json(newTag));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }
}
