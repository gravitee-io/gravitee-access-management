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
import io.gravitee.am.management.handlers.management.api.model.UserEntity;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NewsletterResourceTest extends JerseySpringTest {

    @Test
    public void should_provide_safe_userInfo() {
        User orgUser = new User();
        orgUser.setId(UUID.randomUUID().toString());
        orgUser.setExternalId(UUID.randomUUID().toString());
        orgUser.setPassword(UUID.randomUUID().toString());
        orgUser.setUsername(UUID.randomUUID().toString());
        orgUser.setEmail(UUID.randomUUID().toString());
        when(commonOrganizationUserService.findById(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), any())).thenReturn(Single.just(orgUser));
        when(commonOrganizationUserService.update(any())).thenAnswer(a -> Single.just(a.getArguments()[0]));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        doNothing().when(newsletterService).subscribe(captor.capture());


        final Response response = target("user").path("newsletter").path("_subscribe").request().post(Entity.json(Map.of("email", "test@acme.com")));
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Map responseEntity = readEntity(response, Map.class);
        assertNull(responseEntity.get("password"));
    }
}
