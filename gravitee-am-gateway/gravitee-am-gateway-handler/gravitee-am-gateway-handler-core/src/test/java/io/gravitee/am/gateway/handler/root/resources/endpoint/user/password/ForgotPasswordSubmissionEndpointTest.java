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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.EmailFormatInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.http.HttpHeader;
import io.gravitee.common.http.HttpStatusCode;
import io.netty.handler.codec.Headers;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.multipart.FormDataPart;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ForgotPasswordSubmissionEndpointTest extends RxWebTestBase {

    @Mock
    private UserService userService;

    @Mock
    private Domain domain;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        ForgotPasswordSubmissionEndpoint forgotPasswordSubmissionEndpoint = new ForgotPasswordSubmissionEndpoint(userService, domain);
        router.route(HttpMethod.POST, "/forgotPassword").handler(forgotPasswordSubmissionEndpoint).failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldInvokeForgotPasswordEndpoint() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router
            .route()
            .order(-1)
            .handler(
                routingContext -> {
                    routingContext.put("client", client);
                    routingContext.next();
                }
            );

        when(userService.forgotPassword(eq("email@test.com"), eq(client), any(User.class))).thenReturn(Completable.complete());

        testRequest(
            HttpMethod.POST,
            "/forgotPassword?email=email@test.com",
            null,
            resp -> {
                String location = resp.headers().get("location");
                assertNotNull(location);
                assertTrue(location.endsWith("/forgotPassword?success=forgot_password_completed&client_id=client-id"));
            },
            HttpStatusCode.FOUND_302,
            "Found",
            null
        );
    }

    @Test
    public void shouldCompleteWhenUserNotFoundException() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router
            .route()
            .order(-1)
            .handler(
                routingContext -> {
                    routingContext.put("client", client);
                    routingContext.next();
                }
            );

        when(userService.forgotPassword(eq("email@test.com"), eq(client), any(User.class)))
            .thenReturn(Completable.error(new UserNotFoundException("email@test.com")));

        testRequest(
            HttpMethod.POST,
            "/forgotPassword?email=email@test.com",
            null,
            resp -> {
                String location = resp.headers().get("location");
                assertNotNull(location);
                assertTrue(location.endsWith("/forgotPassword?success=forgot_password_completed&client_id=client-id"));
            },
            HttpStatusCode.FOUND_302,
            "Found",
            null
        );
    }

    @Test
    public void shouldFailWhenEmailFormatInvalidException() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router
            .route()
            .order(-1)
            .handler(
                routingContext -> {
                    routingContext.put("client", client);
                    routingContext.next();
                }
            );

        when(userService.forgotPassword(eq("email.test.com"), eq(client), any(User.class)))
            .thenReturn(Completable.error(new EmailFormatInvalidException("email.test.com")));

        testRequest(
            HttpMethod.POST,
            "/forgotPassword?email=email.test.com",
            null,
            resp -> {
                String location = resp.headers().get("location");
                assertNotNull(location);
                assertTrue(location.endsWith("/forgotPassword?error=forgot_password_failed&client_id=client-id"));
            },
            HttpStatusCode.FOUND_302,
            "Found",
            null
        );
    }
}
