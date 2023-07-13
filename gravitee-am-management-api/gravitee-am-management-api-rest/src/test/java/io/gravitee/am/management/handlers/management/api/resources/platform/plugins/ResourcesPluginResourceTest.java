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
package io.gravitee.am.management.handlers.management.api.resources.platform.plugins;

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.plugin.IdentityProviderPlugin;
import io.gravitee.am.service.model.plugin.ResourcePlugin;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourcesPluginResourceTest extends JerseySpringTest {

    @Test
    public void shouldList() {
        final ResourcePlugin resourcePlugin = new ResourcePlugin();
        resourcePlugin.setId("res-plugin-id");
        resourcePlugin.setName("res-plugin-name");
        resourcePlugin.setDescription("res-desc");
        resourcePlugin.setVersion("1");

        doReturn(Single.just(Collections.singletonList(resourcePlugin))).when(resourcePluginService).findAll(new ArrayList<>());

        final Response response = target("platform")
                .path("plugins")
                .path("resources")
                .request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

}
