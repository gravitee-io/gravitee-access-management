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
package io.gravitee.am.gateway.handler.root.resources.handler.user.password;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.model.User;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResetPasswordOneTimeTokenHandlerTest extends RxWebTestBase {

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route("/resetPassword")
                .handler(BodyHandler.create())
                .handler(new ResetPasswordOneTimeTokenHandler())
                .handler(rc -> rc.response().end())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldHandle_first_time_reset_password() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setLastPasswordReset(null);
            routingContext.put(ConstantKeys.USER_CONTEXT_KEY, endUser);
            routingContext.next();
        });

        testRequest(HttpMethod.POST, "/resetPassword", req -> {
            Buffer buffer = Buffer.buffer();
            buffer.appendString("password=password");
            req.headers().set("content-length", String.valueOf(buffer.length()));
            req.headers().set("content-type", "application/x-www-form-urlencoded");
            req.write(buffer);
        },200, "OK", null);
    }

    @Test
    public void shouldHandle_valid_token() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setLastPasswordReset(new Date());

            JWT token = new JWT();
            token.setIat(Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond());

            routingContext.put(ConstantKeys.TOKEN_CONTEXT_KEY, token);
            routingContext.put(ConstantKeys.USER_CONTEXT_KEY, endUser);
            routingContext.next();
        });

        testRequest(HttpMethod.POST, "/resetPassword", req -> {
            Buffer buffer = Buffer.buffer();
            buffer.appendString("password=password");
            req.headers().set("content-length", String.valueOf(buffer.length()));
            req.headers().set("content-type", "application/x-www-form-urlencoded");
            req.write(buffer);
        },200, "OK", null);
    }

    @Test
    public void shouldNotHandle_invalid_token() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setLastPasswordReset(new Date());

            JWT token = new JWT();
            token.setIat(Instant.now().minus(1, ChronoUnit.HOURS).getEpochSecond());

            routingContext.put(ConstantKeys.TOKEN_CONTEXT_KEY, token);
            routingContext.put(ConstantKeys.USER_CONTEXT_KEY, endUser);
            routingContext.next();
        });

        testRequest(HttpMethod.POST, "/resetPassword", req -> {
            Buffer buffer = Buffer.buffer();
            buffer.appendString("password=password");
            req.headers().set("content-length", String.valueOf(buffer.length()));
            req.headers().set("content-type", "application/x-www-form-urlencoded");
            req.write(buffer);
        }, resp -> {
            String location = resp.headers().get("location");
            assertNotNull(location);
            assertTrue(location.contains("error=invalid_token"));
        },302, "Found", null);
    }

}
