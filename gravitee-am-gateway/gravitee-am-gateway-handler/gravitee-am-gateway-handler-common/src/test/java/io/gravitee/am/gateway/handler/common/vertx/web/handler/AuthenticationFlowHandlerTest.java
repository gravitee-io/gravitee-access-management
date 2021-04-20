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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.AuthenticationFlowHandlerImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.web.handler.FormLoginHandler;
import io.vertx.reactivex.ext.web.handler.SessionHandler;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationFlowHandlerTest extends RxWebTestBase {

    @Mock
    private Domain domain;

    @Mock
    private UserAuthProvider authProvider;

    @InjectMocks
    private AuthenticationFlowHandler authenticationFlowHandler = new AuthenticationFlowHandlerImpl();

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void shouldHaveReturnUrl_autoLogin() throws Exception {
        User user = new User();
        user.setId("user_id");

        router
            .route()
            .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
            .handler(
                routingContext -> {
                    routingContext.setUser(
                        new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user))
                    );
                    routingContext.next();
                }
            )
            .handler(authenticationFlowHandler.create())
            .handler(
                rc -> {
                    final String returnUrl = rc.session().get(FormLoginHandler.DEFAULT_RETURN_URL_PARAM);
                    if (returnUrl != null && returnUrl.contains("/oauth/authorize")) {
                        rc.response().end();
                    } else {
                        rc.response().setStatusCode(500).end();
                    }
                }
            )
            .failureHandler(rc -> rc.response().setStatusCode(503).end());

        testRequest(
            HttpMethod.GET,
            "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/authorize/callback",
            null,
            HttpStatusCode.OK_200,
            "OK",
            null
        );
    }
}
