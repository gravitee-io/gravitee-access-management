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

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSessionHandler;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.UserService;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.exceptions.misusing.InvalidUseOfMatchersException;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class SSOSessionHandlerTest extends RxWebTestBase {

    @Mock
    private ClientSyncService clientSyncService;
    @Mock
    private JWTService jwtService;
    @Mock
    private CertificateManager certificateManager;
    @Mock
    private UserService userService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        when(jwtService.encode(any(JWT.class), (CertificateProvider) eq(null))).thenReturn(Single.just("token"));

        router.route("/login")
                .handler(new CookieSessionHandler(jwtService, certificateManager, userService, "am-cookie", 30 * 60 * 60))
                .handler(new SSOSessionHandler(clientSyncService))
                .handler(rc -> {
                    if (rc.session().isDestroyed()) {
                        rc.response().setStatusCode(401).end();
                    } else {
                        rc.response().setStatusCode(200).end();
                    }
                })
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldInvoke_noUser() throws Exception {
        testRequest(
                HttpMethod.GET,
                "/login",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldInvoke_userDisabled() throws Exception {
        User user = new User();
        user.setEnabled(false);

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login?client_id=test-client",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldInvoke_user_password_reset() throws Exception {
        User user = new User();
        user.setId("user-id");
        user.setLastPasswordReset(new Date(System.currentTimeMillis() - 1000 * 60));

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login?client_id=test-client",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldNotInvoke_user_password_reset() throws Exception {
        User user = new User();
        user.setId("user-id");
        user.setLastPasswordReset(new Date(System.currentTimeMillis() + 1000 * 60));

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login?client_id=test-client",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldInvoke_sameClient() throws Exception {
        User user = new User();
        user.setId("user-id");
        user.setClient("test-client");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("test-client");

        when(clientSyncService.findById(anyString())).thenReturn(Maybe.empty());
        when(clientSyncService.findByClientId(client.getClientId())).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login?client_id=test-client",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldInvoke_differentClient_sameIdp() throws Exception {
        User user = new User();
        user.setId("user-id");
        user.setClient("test-client");
        user.setSource("idp-1");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("test-client");

        Client requestedClient = new Client();
        requestedClient.setId("client-requested-id");
        requestedClient.setClientId("requested-client");
        requestedClient.setIdentities(Collections.singleton("idp-1"));

        when(clientSyncService.findById(anyString())).thenReturn(Maybe.empty()).thenReturn(Maybe.empty());
        when(clientSyncService.findByClientId(anyString())).thenAnswer(
                invocation -> {
                    String argument = invocation.getArgument(0);
                    if (argument.equals("test-client")) {
                        return Maybe.just(client);
                    } else if (argument.equals("requested-client")) {
                        return Maybe.just(requestedClient);
                    }
                    throw new InvalidUseOfMatchersException(
                            String.format("Argument %s does not match", argument)
                    );
                }
        );

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login?client_id=requested-client",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldInvoke_differentClient_differentIdp() throws Exception {
        User user = new User();
        user.setId("user-id");
        user.setClient("test-client");
        user.setSource("idp-1");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("test-client");

        Client requestedClient = new Client();
        requestedClient.setId("client-requested-id");
        requestedClient.setClientId("requested-client");
        requestedClient.setIdentities(Collections.singleton("idp-2"));

        when(clientSyncService.findById(anyString())).thenReturn(Maybe.empty());
        when(clientSyncService.findByClientId(anyString())).thenAnswer(
                invocation -> {
                    String argument = invocation.getArgument(0);
                    if (argument.equals("test-client")) {
                        return Maybe.just(client);
                    } else if (argument.equals("requested-client")) {
                        return Maybe.just(requestedClient);
                    }
                    throw new InvalidUseOfMatchersException(
                            String.format("Argument %s does not match", argument)
                    );
                }
        );

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login?client_id=requested-client",
                HttpStatusCode.FORBIDDEN_403, "Forbidden");
    }
}
