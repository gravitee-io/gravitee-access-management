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
package io.gravitee.am.gateway.handler.vertx.endpoint;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.revocation.RevocationTokenService;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.auth.user.Client;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.revocation.RevocationTokenEndpointHandler;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.Completable;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RevocationTokenEndpointHandlerTest extends RxWebTestBase {

    @InjectMocks
    private RevocationTokenEndpointHandler revocationTokenEndpointHandler = new RevocationTokenEndpointHandler();

    @Mock
    private RevocationTokenService revocationTokenService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.POST, "/oauth/revoke")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(revocationTokenEndpointHandler);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldNotInvokeRevocationTokenEndpoint_noClient() throws Exception {
        testRequest(
                HttpMethod.POST, "/oauth/revoke",
                req -> req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldNotInvokeRevocationTokenEndpoint_noTokenProvided() throws Exception {
        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client(new io.gravitee.am.model.Client())));
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.POST, "/oauth/revoke",
                req -> req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotInvokeRevocationTokenEndpoint_invalidClient() throws Exception {
        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client(new io.gravitee.am.model.Client())));
                routingContext.next();
            }
        });

        when(revocationTokenService.revoke(any())).thenReturn(Completable.error(new InvalidGrantException()));

        testRequest(
                HttpMethod.POST, "/oauth/revoke?token=toto",
                req -> req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotInvokeRevocationTokenEndpoint_invalidToken() throws Exception {
        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client(new io.gravitee.am.model.Client())));
                routingContext.next();
            }
        });

        // invalid token results on completable to complete without error
        when(revocationTokenService.revoke(any())).thenReturn(Completable.complete());

        testRequest(
                HttpMethod.POST, "/oauth/revoke?token=toto",
                req -> req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldNotInvokeRevocationTokenEndpoint_runtimeException() throws Exception {
        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client(new io.gravitee.am.model.Client())));
                routingContext.next();
            }
        });

        // invalid token results on completable to complete without error
        when(revocationTokenService.revoke(any())).thenReturn(Completable.error(new RuntimeException()));

        testRequest(
                HttpMethod.POST, "/oauth/revoke?token=toto",
                req -> req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
                HttpStatusCode.SERVICE_UNAVAILABLE_503, "Service Unavailable", null);
    }
}
