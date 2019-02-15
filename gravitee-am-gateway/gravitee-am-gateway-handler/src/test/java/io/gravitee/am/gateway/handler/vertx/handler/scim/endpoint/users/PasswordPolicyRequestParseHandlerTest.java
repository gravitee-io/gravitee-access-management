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
package io.gravitee.am.gateway.handler.vertx.handler.scim.endpoint.users;

import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user.PasswordPolicyRequestParseHandler;
import io.gravitee.am.gateway.handler.vertx.handler.scim.handler.ErrorHandler;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PasswordPolicyRequestParseHandlerTest extends RxWebTestBase {

    @Mock
    private PasswordValidator passwordValidator;

    @InjectMocks
    private PasswordPolicyRequestParseHandler passwordPolicyRequestParseHandler = new PasswordPolicyRequestParseHandler(passwordValidator);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route()
                .handler(BodyHandler.create())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldNotHandle_invalid_password() throws Exception {
        router.route("/")
                .handler(passwordPolicyRequestParseHandler)
                .handler(rc -> rc.response().end());

        when(passwordValidator.validate(anyString())).thenReturn(false);

        testRequest(HttpMethod.POST, "/", req -> {
            Buffer buffer = Buffer.buffer();
            buffer.appendString("password=password");
            req.headers().set("content-length", String.valueOf(buffer.length()));
            req.headers().set("content-type", "application/x-www-form-urlencoded");
            req.write(buffer);
        }, resp -> {
            String location = resp.headers().get("location");
            assertNotNull(location);
            assertTrue(location.contains("warning=invalid_password_value"));
        },302, "Found", null);
    }

    @Test
    public void shouldHandle() throws Exception {
        router.route("/")
                .handler(passwordPolicyRequestParseHandler)
                .handler(rc -> rc.response().end());

        when(passwordValidator.validate(anyString())).thenReturn(true);

        testRequest(HttpMethod.POST, "/", req -> {
            Buffer buffer = Buffer.buffer();
            buffer.appendString("password=password");
            req.headers().set("content-length", String.valueOf(buffer.length()));
            req.headers().set("content-type", "application/x-www-form-urlencoded");
            req.write(buffer);
        },200, "OK", null);
    }
}
