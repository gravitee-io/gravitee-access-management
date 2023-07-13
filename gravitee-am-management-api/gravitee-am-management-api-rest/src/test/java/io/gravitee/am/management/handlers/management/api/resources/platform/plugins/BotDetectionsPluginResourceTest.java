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
import io.gravitee.am.service.model.plugin.BotDetectionPlugin;
import io.gravitee.am.service.model.plugin.ResourcePlugin;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.core.Response;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BotDetectionsPluginResourceTest extends JerseySpringTest {

    @Test
    public void shouldList() {
        final BotDetectionPlugin botDetectionPlugin = new BotDetectionPlugin();
        botDetectionPlugin.setId("plugin-id");
        botDetectionPlugin.setName("plugin-name");
        botDetectionPlugin.setDescription("desc");
        botDetectionPlugin.setVersion("1");

        doReturn(Single.just(Collections.singletonList(botDetectionPlugin))).when(botDetectionPluginService).findAll();

        final Response response = target("platform")
                .path("plugins")
                .path("bot-detections")
                .request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

}
