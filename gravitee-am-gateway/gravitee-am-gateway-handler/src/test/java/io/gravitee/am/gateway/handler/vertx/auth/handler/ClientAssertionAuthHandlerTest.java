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
package io.gravitee.am.gateway.handler.vertx.auth.handler;

import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientAssertionAuthHandlerTest extends GraviteeAuthHandlerTestBase {

    private final static String CLIENT_ID = "my-client";

    @Before
    public void setUpHandlerAndRouter() {
        Handler<RoutingContext> handler = rc -> {
            assertNotNull(rc.user());
            assertEquals(CLIENT_ID, rc.user().principal().getString(OAuth2Constants.CLIENT_ID));
            rc.response().end();
        };

        router.route("/token/*")
                .handler(ClientAssertionAuthHandler.create(new DummyClientAssertionAuthProvider()).getDelegate())
                .handler(handler);
    }

    @Test
    public void unauthorized_noCredentials() throws Exception {
        testRequest(HttpMethod.POST, "/token/", 401, "Unauthorized");
    }

    @Test
    public void success() throws Exception {
        testRequest(HttpMethod.POST, "/token?client_assertion_type=type&client_assertion=myToken", 200, "OK");
    }

    @Override
    protected AuthHandler createAuthHandler(AuthProvider authProvider) {
        return ClientAssertionAuthHandler.create(authProvider).getDelegate();
    }

    class DummyClientAssertionAuthProvider implements AuthProvider {

        @Override
        public void authenticate(JsonObject jsonObject, Handler<AsyncResult<User>> handler) {
            if (jsonObject.getString("client_assertion_type") == null || jsonObject.getString("client_assertion") == null) {
                handler.handle(Future.failedFuture("no-assertion"));
            } else {
                handler.handle(Future.succeededFuture(new AbstractUser() {
                    @Override
                    protected void doIsPermitted(String s, Handler<AsyncResult<Boolean>> handler) {

                    }

                    @Override
                    public JsonObject principal() {
                        return new JsonObject().put(OAuth2Constants.CLIENT_ID, CLIENT_ID);
                    }

                    @Override
                    public void setAuthProvider(AuthProvider authProvider) {

                    }
                }));
            }
        }
    }


}
