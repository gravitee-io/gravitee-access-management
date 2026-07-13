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
import io.gravitee.am.model.ConsoleUserPreferences;
import io.gravitee.am.model.Organization;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * @author GraviteeSource Team
 */
public class CurrentUserPreferencesResourceTest extends JerseySpringTest {

    @BeforeEach
    public void initialize() {
        reset(organizationUserService);
    }

    @Test
    public void shouldGetPreferences() {
        final ConsoleUserPreferences preferences = new ConsoleUserPreferences("domain-1", "env-1", List.of("domain-1", "domain-2"));
        doReturn(Single.just(preferences)).when(organizationUserService).getConsolePreferences(eq(Organization.DEFAULT), any());

        final Response response = target("user").path("preferences").request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final ConsoleUserPreferences entity = readEntity(response, ConsoleUserPreferences.class);
        assertEquals("domain-1", entity.getDefaultDomainId());
        assertEquals("env-1", entity.getDefaultEnvironmentId());
        assertEquals(List.of("domain-1", "domain-2"), entity.getPinnedDomainIds());
    }

    @Test
    public void shouldUpdatePreferences() {
        final ConsoleUserPreferences preferences = new ConsoleUserPreferences("domain-1", "env-1", List.of("domain-1"));
        doReturn(Single.just(preferences)).when(organizationUserService).updateConsolePreferences(eq(Organization.DEFAULT), any(), any());

        final Response response = target("user").path("preferences").request().put(Entity.json(preferences));

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        verify(organizationUserService).updateConsolePreferences(eq(Organization.DEFAULT), any(),
                argThat(p -> "domain-1".equals(p.getDefaultDomainId()) && List.of("domain-1").equals(p.getPinnedDomainIds())));
    }

    @Test
    public void shouldNotUpdatePreferences_tooManyPinnedDomains() {
        final List<String> pinned = IntStream.range(0, 51).mapToObj(i -> "domain-" + i).toList();
        final ConsoleUserPreferences preferences = new ConsoleUserPreferences(null, null, pinned);

        final Response response = target("user").path("preferences").request().put(Entity.json(preferences));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }
}
