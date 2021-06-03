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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.register;

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterFailureHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterProcessHandler;
import io.gravitee.am.gateway.handler.root.service.response.RegistrationResponse;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.EmailFormatInvalidException;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RegisterSubmissionEndpointTest extends RxWebTestBase {

    @Mock
    private UserService userService;

    @Mock
    private Domain domain;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.POST, "/register")
                .handler(BodyHandler.create())
                .handler(new RegisterProcessHandler(userService, domain))
                .handler(new RegisterSubmissionEndpoint())
                .failureHandler(new RegisterFailureHandler());
    }

    @Test
    public void shouldInvokeRegisterEndpoint() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.next();
        });

        when(userService.register(eq(client), any(), any())).thenReturn(Single.just(new RegistrationResponse()));

        testRequest(
                HttpMethod.POST, "/register?client_id=client-id",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/register?client_id=client-id&success=registration_succeed"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeRegisterEndpoint_redirectUri() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        RegistrationResponse registrationResponse = new RegistrationResponse();
        registrationResponse.setAutoLogin(true);
        registrationResponse.setRedirectUri("http://custom_uri");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.next();
        });

        when(userService.register(eq(client), any(), any())).thenReturn(Single.just(registrationResponse));

        testRequest(
                HttpMethod.POST, "/register?client_id=client-id",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://custom_uri", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldFail_UserAlreadyExistsException() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.next();
        });

        when(userService.register(eq(client), any(), any())).thenReturn(Single.error(new UserAlreadyExistsException("test")));

        testRequest(
                HttpMethod.POST, "/register?client_id=client-id",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/register?client_id=client-id&error=registration_failed"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldFail_invalidUserException() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.next();
        });

        when(userService.register(eq(client), any(), any())).thenReturn(Single.error(new InvalidUserException("Username invalid")));

        testRequest(
                HttpMethod.POST, "/register?client_id=client-id",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/register?client_id=client-id&warning=invalid_user_information"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldFail_emailFormatInvalidException() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.next();
        });

        when(userService.register(eq(client), any(), any())).thenReturn(Single.error(new EmailFormatInvalidException("test")));

        testRequest(
                HttpMethod.POST, "/register?client_id=client-id",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/register?client_id=client-id&warning=invalid_email"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
