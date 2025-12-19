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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.MediaType;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author GraviteeSource Team
 */
public class RememberedLoginPageHandlerTest extends RxWebTestBase {

    @Override
    public void setUp() throws Exception {
        super.setUp();

        ThymeleafTemplateEngine templateEngine = Mockito.mock(ThymeleafTemplateEngine.class);
        DeviceIdentifierManager deviceIdentifierManager = Mockito.mock(DeviceIdentifierManager.class);

        Mockito.when(templateEngine.render(Mockito.anyMap(), Mockito.anyString()))
                .thenReturn(io.reactivex.rxjava3.core.Single.just(Buffer.buffer("remembered-login-page")));

        router.get(RootProvider.PATH_LOGIN + "/remembered")
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    rc.next();
                })
                .handler(new RememberedLoginPageHandler(templateEngine, deviceIdentifierManager));
    }

    @Test
    public void shouldRenderRememberedLoginPage_withExpectedContentType() throws Exception {
        testRequest(
                HttpMethod.GET,
                RootProvider.PATH_LOGIN + "/remembered?param=value",
                null,
                response -> {
                    String contentType = response.getHeader("Content-Type");
                    assertEquals(MediaType.TEXT_HTML, contentType);
                },
                200,
                "OK",
                null);
    }
}
