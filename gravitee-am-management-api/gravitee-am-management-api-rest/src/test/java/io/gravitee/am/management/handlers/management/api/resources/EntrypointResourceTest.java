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
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.service.exception.EntrypointNotFoundException;
import io.gravitee.am.service.model.UpdateEntrypoint;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.core.Response;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EntrypointResourceTest extends JerseySpringTest {

    public static final String ORGANIZATION_ID = "orga#1";
    public static final String ENTRYPOINT_ID = "entrypoint#1";

    @Test
    public void shouldGetEntrypoint() {

        final Entrypoint mockEntrypoint = new Entrypoint();
        mockEntrypoint.setId(ENTRYPOINT_ID);
        mockEntrypoint.setOrganizationId(ORGANIZATION_ID);
        mockEntrypoint.setName("name");
        mockEntrypoint.setDescription("description");
        mockEntrypoint.setOrganizationId(ORGANIZATION_ID);
        mockEntrypoint.setTags(Arrays.asList("tag#1", "tag#2"));
        mockEntrypoint.setCreatedAt(new Date());
        mockEntrypoint.setUpdatedAt(new Date());

        doReturn(Single.just(mockEntrypoint)).when(entrypointService).findById(ENTRYPOINT_ID, ORGANIZATION_ID);

        final Response response = target("organizations")
                .path(ORGANIZATION_ID)
                .path("entrypoints").path(ENTRYPOINT_ID).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Entrypoint entrypoint = readEntity(response, Entrypoint.class);
        assertEquals(mockEntrypoint.getId(), entrypoint.getId());
        assertEquals(mockEntrypoint.getOrganizationId(), entrypoint.getOrganizationId());
        assertEquals(mockEntrypoint.getName(), entrypoint.getName());
        assertEquals(mockEntrypoint.getDescription(), entrypoint.getDescription());
        assertEquals(mockEntrypoint.getUrl(), entrypoint.getUrl());
        assertEquals(mockEntrypoint.getTags(), entrypoint.getTags());
        assertEquals(mockEntrypoint.getCreatedAt(), entrypoint.getCreatedAt());
        assertEquals(mockEntrypoint.getUpdatedAt(), entrypoint.getUpdatedAt());
    }

    @Test
    public void shouldNotGetEntrypoint_notFound() {

        doReturn(Single.error(new EntrypointNotFoundException(ENTRYPOINT_ID))).when(entrypointService).findById(ENTRYPOINT_ID, ORGANIZATION_ID);

        final Response response = target("organizations")
                .path(ORGANIZATION_ID)
                .path("entrypoints").path(ENTRYPOINT_ID).request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldUpdateEntrypoint() {

        UpdateEntrypoint updateEntrypoint = new UpdateEntrypoint();
        updateEntrypoint.setName("name");
        updateEntrypoint.setUrl("https://auth.company.com");
        updateEntrypoint.setTags(Collections.emptyList());

        final Entrypoint mockEntrypoint = new Entrypoint();
        mockEntrypoint.setId(ENTRYPOINT_ID);
        mockEntrypoint.setOrganizationId(ORGANIZATION_ID);
        mockEntrypoint.setName("name");

        doReturn(Single.just(mockEntrypoint)).when(entrypointService).update(eq(ENTRYPOINT_ID), eq(ORGANIZATION_ID), any(UpdateEntrypoint.class), any(User.class));

        final Response response = put(target("organizations")
                .path(ORGANIZATION_ID)
                .path("entrypoints").path(ENTRYPOINT_ID), updateEntrypoint);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Entrypoint entrypoint = readEntity(response, Entrypoint.class);
        assertEquals(mockEntrypoint.getId(), entrypoint.getId());
        assertEquals(mockEntrypoint.getOrganizationId(), entrypoint.getOrganizationId());
        assertEquals(mockEntrypoint.getName(), entrypoint.getName());
    }

    @Test
    public void shouldNotUpdateEntrypoint_notFound() {

        UpdateEntrypoint updateEntrypoint = new UpdateEntrypoint();
        updateEntrypoint.setName("name");
        updateEntrypoint.setUrl("https://auth.company.com");
        updateEntrypoint.setTags(Collections.emptyList());

        doReturn(Single.error(new EntrypointNotFoundException(ENTRYPOINT_ID))).when(entrypointService).update(eq(ENTRYPOINT_ID), eq(ORGANIZATION_ID), any(UpdateEntrypoint.class), any(User.class));

        final Response response = put(target("organizations")
                .path(ORGANIZATION_ID)
                .path("entrypoints").path(ENTRYPOINT_ID), updateEntrypoint);

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }
}
