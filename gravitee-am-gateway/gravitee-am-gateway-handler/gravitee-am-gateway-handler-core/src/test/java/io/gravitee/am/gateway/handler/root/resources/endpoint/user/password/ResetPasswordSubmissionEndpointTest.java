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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.password;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.root.service.response.ResetPasswordResponse;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Session;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.Collections;

import static io.vertx.core.http.HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ResetPasswordSubmissionEndpointTest extends RxWebTestBase {

    @Mock
    private UserService userService;
    @Mock
    private Environment environment;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        when(environment.getProperty(eq(ResetPasswordSubmissionEndpoint.GATEWAY_ENDPOINT_RESET_PWD_KEEP_PARAMS), any(), eq(true))).thenReturn(true);

        ResetPasswordSubmissionEndpoint resetPasswordSubmissionEndpoint = new ResetPasswordSubmissionEndpoint(userService, environment);
        router.route(HttpMethod.POST, "/resetPassword")
                .handler(BodyHandler.create())
                .handler(resetPasswordSubmissionEndpoint)
                .failureHandler(new ErrorHandler());
    }


    @Test
    public void shouldRedirectToClaimAfterForcePasswordReset() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        User user = new User();
        user.setId("user-id");

        JWT jwt = new JWT();
        jwt.setSub(user.getId());
        jwt.setAud(client.getId());
        jwt.setIat(System.currentTimeMillis());
        jwt.put(ConstantKeys.CLAIM_QUERY_PARAM, "client_id=client-id&response_type=code&redirect_uri=https%3A%2F%2Fwebapp");

        String jwtToken = "eyJraWQiOiJkZWZhdWx0LWdyYXZpdGVlLUFNLWtleSIsInR5cCI6IkpXVCIsImFsZyI6IkhTMjU2In0.eyJzdWIiOiJ1c2VyLWlkIiwiYXVkIjoiY2xpZW50LWlkIiwiaWF0IjoxNzE0MTMxODE1NjI0LCJjbGFpbXNfcmVxdWVzdF9wYXJhbWV0ZXIiOiIvYXV0aG9yaXplP2NsaWVudF9pZD1jbGllbnQtaWQmcmVzcG9uc2VfdHlwZT1jb2RlJnJlZGlyZWN0X3VyaT1odHRwcyUzQSUyRiUyRndlYmFwcCJ9.-D_OeGamCN3xciwUUKwZYBvmsk1-zPjFUz_FD2GPHGE";
        router.route().order(-1)
        .handler(routingContext -> {
            routingContext.getDelegate().setSession(mock(Session.class));
            routingContext.put("client", client);
            routingContext.put("user", user);
            routingContext.put("token", jwt);
            routingContext.next();
        });

        when(userService.resetPassword(eq(client), eq(user), any())).thenReturn(Single.just(new ResetPasswordResponse()));

        testRequest(
                HttpMethod.POST, "/resetPassword?client_id=client-id&token=" + jwtToken,
                this::postPassword,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("authorize?client_id=client-id&response_type=code&redirect_uri=https%3A%2F%2Fwebapp"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNOTRedirectToAuthorizationAfterForcePasswordReset_missingClaim() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        User user = new User();
        user.setId("user-id");

        JWT jwt = new JWT();
        jwt.setSub(user.getId());
        jwt.setAud(client.getId());
        jwt.setIat(System.currentTimeMillis());

        String jwtToken = "eyJraWQiOiJkZWZhdWx0LWdyYXZpdGVlLUFNLWtleSIsInR5cCI6IkpXVCIsImFsZyI6IkhTMjU2In0.eyJzdWIiOiJ1c2VyLWlkIiwiYXVkIjoiY2xpZW50LWlkIiwiaWF0IjoxNzE0MTMxODE1NjI0fQ.UuqhK0mg_378I7-r7GkNvwkr9MYiaQGwuCYKx8zEFAw";
        router.route().order(-1).handler(routingContext -> {
            routingContext.getDelegate().setSession(mock(Session.class));
            routingContext.put("client", client);
            routingContext.put("user", user);
            routingContext.put("token", jwt);
            routingContext.next();
        });

        when(userService.resetPassword(eq(client), eq(user), any())).thenReturn(Single.just(new ResetPasswordResponse()));

        testRequest(
                HttpMethod.POST, "/resetPassword?client_id=client-id&token=" + jwtToken,
                this::postPassword,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/resetPassword?client_id=client-id&success=reset_password_completed"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeResetPasswordEndpoint() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        User user = new User();

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.put("user", user);
            routingContext.next();
        });

        when(userService.resetPassword(eq(client), eq(user), any())).thenReturn(Single.just(new ResetPasswordResponse()));

        testRequest(
                HttpMethod.POST, "/resetPassword?client_id=client-id",
                this::postPassword,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/resetPassword?client_id=client-id&success=reset_password_completed"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }


    @Test
    public void shouldInvokeResetPasswordEndpoint_redirectUri() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        User user = new User();

        ResetPasswordResponse resetPasswordResponse = new ResetPasswordResponse();
        resetPasswordResponse.setAutoLogin(true);
        resetPasswordResponse.setUser(user);
        resetPasswordResponse.setRedirectUri("http://custom_uri");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.put("user", user);
            routingContext.next();
        });

        when(userService.resetPassword(eq(client), eq(user), any())).thenReturn(Single.just(resetPasswordResponse));

        testRequest(
                HttpMethod.POST, "/resetPassword?client_id=client-id",
                this::postPassword,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://custom_uri?client_id=client-id", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldDestroySession_whenAutoLoginIsFalse() throws Exception {
        verifySessionDestructionBehavior(false, true);
    }

    @Test
    public void shouldNotDestroySession_whenAutoLoginIsTrue() throws Exception {
        verifySessionDestructionBehavior(true, false);
    }

    private void verifySessionDestructionBehavior(boolean autoLogin, boolean expectDestroy) throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        User user = new User();

        ResetPasswordResponse resetPasswordResponse = new ResetPasswordResponse();
        resetPasswordResponse.setAutoLogin(autoLogin);
        resetPasswordResponse.setUser(user);

        Session session = mock(Session.class);

        router.route().order(-1).handler(routingContext -> {
            routingContext.getDelegate().setSession(session);
            routingContext.put("client", client);
            routingContext.put("user", user);
            routingContext.next();
        });

        when(userService.resetPassword(eq(client), eq(user), any())).thenReturn(Single.just(resetPasswordResponse));

        testRequest(
                HttpMethod.POST, "/resetPassword?client_id=client-id",
                this::postPassword,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/resetPassword?client_id=client-id&success=reset_password_completed"));
                },
                HttpStatusCode.FOUND_302, "Found", null);

        if (expectDestroy) {
            verify(session).destroy();
        } else {
            verify(session, never()).destroy();
        }
    }

    private void postPassword(io.vertx.rxjava3.core.http.HttpClientRequest httpClientRequest) {
        httpClientRequest.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
        httpClientRequest.setChunked(true);
        httpClientRequest.write(Buffer.buffer("password=toto"));
    }
}
