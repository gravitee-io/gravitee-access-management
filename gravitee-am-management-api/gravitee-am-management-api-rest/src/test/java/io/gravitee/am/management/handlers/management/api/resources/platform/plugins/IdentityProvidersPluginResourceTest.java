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
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProvidersPluginResourceTest extends JerseySpringTest {

    @Test
    public void shouldList() {
        final IdentityProviderPlugin identityProviderPlugin = new IdentityProviderPlugin();
        identityProviderPlugin.setId("identityProvider-plugin-id");
        identityProviderPlugin.setName("identityProvider-plugin-name");

        doReturn(Single.just(Collections.singletonList(identityProviderPlugin))).when(identityProviderPluginService).findAll(false, new ArrayList<>());

        final Response response = target("platform")
                .path("plugins")
                .path("identities")
                .queryParam("external", false)
                .request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldList_technicalManagementException() {
        doReturn(Single.error(new TechnicalManagementException("Error occurs"))).when(identityProviderPluginService).findAll(false, new ArrayList<>());

        final Response response = target("platform")
                .path("plugins")
                .path("identities")
                .queryParam("external", false)
                .request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }
}
