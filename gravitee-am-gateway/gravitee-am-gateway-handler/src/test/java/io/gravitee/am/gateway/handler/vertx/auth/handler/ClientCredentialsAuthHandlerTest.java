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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.AuthHandlerTestBase;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.function.Consumer;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientCredentialsAuthHandlerTest extends AuthHandlerTestBase {

    @Test
    public void shouldLoginSuccess() throws Exception {
        Handler<RoutingContext> handler = rc -> {
            assertNotNull(rc.user());
            assertEquals("my-client", rc.user().principal().getString(OAuth2Constants.CLIENT_ID));
            rc.response().end();
        };

        router.route("/token/*")
                .handler(ClientCredentialsAuthHandler.create(new DummyAuthProvider()).getDelegate())
                .handler(handler);

        testRequest(HttpMethod.GET, "/token/", 401, "Unauthorized");

        // Now try again with credentials
        testRequest(HttpMethod.GET, "/token?client_id=my-client&client_secret=my-secret", resp -> {
            String wwwAuth = resp.headers().get("WWW-Authenticate");
            assertNull(wwwAuth);
        }, 200, "OK", null);
    }

    @Override
    protected AuthHandler createAuthHandler(AuthProvider authProvider) {
        return ClientCredentialsAuthHandler.create(authProvider).getDelegate();
    }

    @Override
    protected HttpServerOptions getHttpServerOptions() {
        return new HttpServerOptions().setPort(RANDOM_PORT).setHost("localhost");
    }

    @Override
    protected HttpClientOptions getHttpClientOptions() {
        return new HttpClientOptions().setDefaultPort(RANDOM_PORT);
    }


    @Override
    protected void testRequestBuffer(HttpMethod method, String path, Consumer<HttpClientRequest> requestAction, Consumer<HttpClientResponse> responseAction, int statusCode, String statusMessage, Buffer responseBodyBuffer, boolean normalizeLineEndings) throws Exception {
        this.testRequestBuffer(this.client, method, RANDOM_PORT, path, requestAction, responseAction, statusCode, statusMessage, responseBodyBuffer, normalizeLineEndings);
    }

    private final static int RANDOM_PORT = lookupAvailablePort();

    public static int lookupAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
        }
        return -1;
    }

    class DummyAuthProvider implements AuthProvider {

        @Override
        public void authenticate(JsonObject jsonObject, Handler<AsyncResult<User>> handler) {
            if (jsonObject.getString("username") == null) {
                handler.handle(Future.failedFuture("no-client-id"));
            } else {
                handler.handle(Future.succeededFuture(new AbstractUser() {
                    @Override
                    protected void doIsPermitted(String s, Handler<AsyncResult<Boolean>> handler) {

                    }

                    @Override
                    public JsonObject principal() {
                        return new JsonObject().put(OAuth2Constants.CLIENT_ID,
                                jsonObject.getString("username"));
                    }

                    @Override
                    public void setAuthProvider(AuthProvider authProvider) {

                    }
                }));
            }
        }
    }
}
