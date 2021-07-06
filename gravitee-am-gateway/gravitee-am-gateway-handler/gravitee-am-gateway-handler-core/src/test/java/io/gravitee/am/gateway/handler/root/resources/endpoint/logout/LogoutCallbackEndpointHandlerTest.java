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
package io.gravitee.am.gateway.handler.root.resources.endpoint.logout;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.resources.handler.error.ErrorHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.TokenService;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LogoutCallbackEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private Domain domain;
    @Mock
    private TokenService tokenService;
    @Mock
    private AuditService auditService;
    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/logout/callback")
                .handler(new LogoutCallbackEndpoint(domain, tokenService, auditService, authenticationFlowContextService))
                .failureHandler(new ErrorHandler("/error"));
    }

    @Test
    public void shouldRedirect_using_state() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY,  RequestUtils.getQueryParams("post_logout_redirect_uri=http://my-app&state=myappstate", false));
            routingContext.put(ConstantKeys.PROVIDER_ID_PARAM_KEY, "provider-id");
            routingContext.put(Parameters.CLIENT_ID, "client-id");
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout/callback?state=myappstate",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("http://my-app?state=myappstate"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirect_using_state_invalidTargetUrl() throws Exception {
        Client client = new Client();
        client.setClientId("client-id");
        client.setPostLogoutRedirectUris(Arrays.asList("https://my-app"));
        client.setSingleSignOut(true);

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY,  RequestUtils.getQueryParams("post_logout_redirect_uri=http://my-invalid-app&state=myappstate", false));
            routingContext.put(ConstantKeys.PROVIDER_ID_PARAM_KEY, "provider-id");
            routingContext.put(Parameters.CLIENT_ID, "client-id");
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout/callback?state=myappstate",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/error?client_id=client-id&error=invalid_request&error_description=The+post_logout_redirect_uri+MUST+match+the+registered+callback+URLs"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
