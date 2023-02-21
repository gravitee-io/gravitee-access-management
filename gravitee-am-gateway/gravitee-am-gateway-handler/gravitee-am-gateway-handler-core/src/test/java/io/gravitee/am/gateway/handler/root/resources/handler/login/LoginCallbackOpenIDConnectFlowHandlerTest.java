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

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginCallbackOpenIDConnectFlowHandlerTest extends RxWebTestBase {

    @Mock
    private ThymeleafTemplateEngine engine;

    private LoginCallbackOpenIDConnectFlowHandler loginCallbackOpenIDConnectFlowHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        loginCallbackOpenIDConnectFlowHandler =
                new LoginCallbackOpenIDConnectFlowHandler(engine);
    }

    @Test
    public void shouldContinue_authorizationCodeFlow() throws Exception {
        router
                .post("/login/callback")
                .handler(loginCallbackOpenIDConnectFlowHandler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.POST, "/login/callback?code=12345",
                null,
                null,
                200, "OK", null);

        verify(engine, never()).render(any(Map.class), any());
    }
}
