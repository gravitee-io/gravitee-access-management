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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;

/**
 * @author GraviteeSource Team
 */
public class RedirectHandlerImplTest extends RxWebTestBase {

    private static final String REDIRECT_PATH = "/redirected";

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(REDIRECT_PATH)
                .handler(rc -> {
                    rc.put(UriBuilderRequest.CONTEXT_PATH, "");
                    rc.next();
                });
    }

    @Test
    public void shouldForwardToken_whenTokenIsAString() throws Exception {
        router.route(REDIRECT_PATH)
                .handler(givenContextHas(ConstantKeys.TOKEN_CONTEXT_KEY, "some-encoded-jwt-string"))
                .handler(new RedirectHandlerImpl(REDIRECT_PATH));

        testRequest(
                HttpMethod.GET,
                REDIRECT_PATH,
                null,
                res -> {
                    String location = res.getHeader("Location");
                    assertTrue(location.contains(ConstantKeys.TOKEN_CONTEXT_KEY + "=some-encoded-jwt-string"));
                },
                302,
                "Found",
                (String) null);
    }

    @Test
    public void shouldNotFailNorAddTokenParam_whenTokenIsNotAString() throws Exception {
        JWT jwt = new JWT();
        jwt.setSub("some-user-id");

        router.route(REDIRECT_PATH)
                .handler(givenContextHas(ConstantKeys.TOKEN_CONTEXT_KEY, jwt))
                .handler(new RedirectHandlerImpl(REDIRECT_PATH));

        testRequest(
                HttpMethod.GET,
                REDIRECT_PATH,
                null,
                res -> {
                    String location = res.getHeader("Location");
                    assertFalse(location.contains(ConstantKeys.TOKEN_CONTEXT_KEY));
                },
                302,
                "Found",
                (String) null);
    }

    @Test
    public void shouldNotAddTokenParam_whenTokenIsAbsent() throws Exception {
        router.route(REDIRECT_PATH)
                .handler(new RedirectHandlerImpl(REDIRECT_PATH));

        testRequest(
                HttpMethod.GET,
                REDIRECT_PATH,
                null,
                res -> {
                    String location = res.getHeader("Location");
                    assertFalse(location.contains(ConstantKeys.TOKEN_CONTEXT_KEY));
                },
                302,
                "Found",
                (String) null);
    }
}
