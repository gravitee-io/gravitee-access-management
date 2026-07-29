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
package io.gravitee.am.gateway.handler.root.resources.handler.user.register;

import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(MockitoJUnitRunner.class)
public class RegisterFailureHandlerTest {

    private final RegisterFailureHandler handler = new RegisterFailureHandler();

    @Mock
    private RoutingContext ctx;

    @Mock
    private HttpServerResponse response;

    @Mock
    private HttpServerRequest request;

    @Mock
    private Session session;

    @Before
    public void setUp() {
        Mockito.when(ctx.response()).thenReturn(response);
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(ctx.get(CONTEXT_PATH)).thenReturn("test-domain");
    }

    @Test
    public void should_redirect_to_error_page_and_store_hash_when_statusCode_is_404() {
        // given: registration is disabled at the router level, hence a 404 status code
        // and any unexpected/unmapped exception (i.e. neither InvalidUserException nor EmailFormatInvalidException)
        Mockito.when(ctx.failure()).thenReturn(new RuntimeException("unexpected error"));
        Mockito.when(ctx.statusCode()).thenReturn(404);
        Mockito.when(ctx.session()).thenReturn(session);
        Mockito.when(session.isDestroyed()).thenReturn(false);

        // when
        handler.doHandle(ctx);

        // then: redirected to the error page (not the register page) with a registration_failed error
        Mockito.verify(response).putHeader(eq("Location"), eq("test-domain/error?error=registration_failed"));

        // and the error is hashed and stored in session so it can't be tampered with via query params
        String expectedHash = HashUtil.generateSHA256("registration_failed");
        Mockito.verify(session).put(eq(ERROR_HASH), eq(expectedHash));
    }

    @Test
    public void should_not_store_hash_when_statusCode_is_404_and_session_is_destroyed() {
        Mockito.when(ctx.failure()).thenReturn(new RuntimeException("unexpected error"));
        Mockito.when(ctx.statusCode()).thenReturn(404);
        Mockito.when(ctx.session()).thenReturn(session);
        Mockito.when(session.isDestroyed()).thenReturn(true);

        // when
        handler.doHandle(ctx);

        // then
        Mockito.verify(response).putHeader(eq("Location"), eq("test-domain/error?error=registration_failed"));
        Mockito.verify(session, Mockito.never()).put(eq(ERROR_HASH), Mockito.anyString());
    }

    @Test
    public void should_redirect_to_register_page_with_generic_success_when_statusCode_is_not_404() {
        // given: same unexpected exception but the request reaches the register page normally (no 404)
        Mockito.when(ctx.failure()).thenReturn(new RuntimeException("unexpected error"));
        Mockito.when(ctx.statusCode()).thenReturn(500);
        Mockito.when(request.path()).thenReturn("/register");

        // when
        handler.doHandle(ctx);

        // then: to avoid account enumeration, the response is masked as a success
        Mockito.verify(response).putHeader(eq("Location"), eq("/register?success=registration_succeed"));
        Mockito.verify(ctx, Mockito.never()).session();
    }
}
