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

package io.gravitee.am.gateway.handler.root.resources.endpoint.user.action;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.policy.UserActionPolicy;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserActionEndpointTest extends RxWebTestBase {

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private FlowManager flowManager;

    @Mock
    private ExecutionContextFactory executionContextFactory;

    @Mock
    private JWTService jwtService;

    @Mock
    private Environment environment;

    private final Domain domain = new Domain();

    @Test
    public void shouldRedirectToAuthorize_whenSingleUserActionPolicy_post_login_identifier() throws Exception {
        shouldRedirectToAuthorize_whenSingleUserActionPolicy(ExtensionPoint.POST_LOGIN_IDENTIFIER, "/login");
    }

    @Test
    public void shouldRedirectToAuthorize_whenSingleUserActionPolicy_post_login() throws Exception {
        shouldRedirectToAuthorize_whenSingleUserActionPolicy(ExtensionPoint.POST_LOGIN, "/oauth/authorize");
    }

    @Test
    public void shouldRedirectToAuthorize_whenSingleUserActionPolicy_post_mfa_enroll() throws Exception {
        shouldRedirectToAuthorize_whenSingleUserActionPolicy(ExtensionPoint.POST_MFA_ENROLLMENT, "/oauth/authorize");
    }

    @Test
    public void shouldRedirectToAuthorize_whenSingleUserActionPolicy_post_mfa_challenge() throws Exception {
        shouldRedirectToAuthorize_whenSingleUserActionPolicy(ExtensionPoint.POST_MFA_CHALLENGE, "/oauth/authorize");
    }

    @Test
    public void shouldRedirectToAuthorize_whenSingleUserActionPolicy_post_consent() throws Exception {
        shouldRedirectToAuthorize_whenSingleUserActionPolicy(ExtensionPoint.POST_CONSENT, "/oauth/authorize");
    }

    @Test
    public void shouldRedirectToAuthorize_whenSingleUserActionPolicy_post_register() throws Exception {
        shouldRedirectToAuthorize_whenSingleUserActionPolicy(ExtensionPoint.POST_REGISTER, "/register?ua_state=state-token&success=registration_succeed");
    }

    @Test
    public void shouldRedirectToAuthorize_whenSingleUserActionPolicy_post_webauthn_register() throws Exception {
        shouldRedirectToAuthorize_whenSingleUserActionPolicy(ExtensionPoint.POST_WEBAUTHN_REGISTER, "/oauth/authorize");
    }

    @Test
    public void shouldReplaceUaStateWhenSeveralUserActionPolicies() throws Exception {
        Client client = new Client();
        client.setClientId("clientId");
        client.setDomain("domain");

        // JWT state points to the first action
        JWT jwt = new JWT();
        jwt.setSub(client.getClientId());
        jwt.setDomain(client.getDomain());
        jwt.put("flow", ExtensionPoint.POST_LOGIN.name());
        jwt.put("action", "first");

        when(jwtService.decodeAndVerify(eq("state-token"), eq(client), eq(JWTService.TokenType.STATE)))
                .thenReturn(Single.just(jwt));

        // Two policies: first then second
        UserActionPolicy first = mock(UserActionPolicy.class);
        when(first.getAction()).thenReturn("first");
        when(first.performUserAction(any(), any())).thenReturn(Completable.complete());

        UserActionPolicy second = mock(UserActionPolicy.class);
        when(second.getAction()).thenReturn("second");

        Policy firstPolicy = mock(Policy.class);
        when(firstPolicy.policyInst()).thenReturn(first);
        Policy secondPolicy = mock(Policy.class);
        when(secondPolicy.policyInst()).thenReturn(second);

        when(flowManager.findByExtensionPoint(eq(ExtensionPoint.POST_LOGIN), eq(client), any()))
                .thenReturn(Single.just(List.of(firstPolicy, secondPolicy)));

        // Execution context required by the endpoint
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getAttributes()).thenReturn(new HashMap<>());
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        when(jwtService.encode(any(JWT.class), eq(client))).thenReturn(Single.just("next-state"));
        when(environment.getProperty(anyString(), any(), anyLong())).thenReturn(18000L);

        UserActionEndpoint endpoint = new UserActionEndpoint(templateEngine, flowManager, executionContextFactory, jwtService, domain, environment);
        router.route(HttpMethod.POST, "/userAction")
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create())
                .handler(routingContext -> {
                    routingContext.put(CLIENT_CONTEXT_KEY, client);
                    routingContext.next();
                })
                .handler(endpoint)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.POST, "/userAction?ua_state=state-token",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/userAction?ua_state=next-state"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldFailWhenUaStateIsMissing() {
        Client client = new Client();
        client.setClientId("clientId");
        client.setDomain("domain");

        RoutingContext routingContext = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);

        when(routingContext.request()).thenReturn(request);
        when(routingContext.get(CLIENT_CONTEXT_KEY)).thenReturn(client);
        io.vertx.core.http.HttpMethod httpMethod = mock(io.vertx.core.http.HttpMethod.class);
        when(httpMethod.name()).thenReturn("POST");
        when(request.method()).thenReturn(httpMethod);
        when(request.getParam("ua_state")).thenReturn(null);

        when(environment.getProperty(anyString(), any(), anyLong())).thenReturn(18000L);

        UserActionEndpoint endpoint = new UserActionEndpoint(templateEngine, flowManager, executionContextFactory, jwtService, domain, environment);
        endpoint.handle(routingContext);

        verify(routingContext).fail(any(Throwable.class));
    }

    private void shouldRedirectToAuthorize_whenSingleUserActionPolicy(ExtensionPoint extensionPoint, String expectedPath) throws Exception {
        Client client = new Client();
        client.setClientId("clientId");
        client.setDomain("domain");

        // JWT state
        JWT jwt = new JWT();
        jwt.setSub(client.getClientId());
        jwt.setDomain(client.getDomain());
        jwt.put("flow", extensionPoint.name());
        jwt.put("action", "test");

        when(jwtService.decodeAndVerify(eq("state-token"), eq(client), eq(JWTService.TokenType.STATE)))
                .thenReturn(Single.just(jwt));

        // a single user action policy
        UserActionPolicy userActionPolicy = mock(UserActionPolicy.class);
        when(userActionPolicy.getAction()).thenReturn("test");
        when(userActionPolicy.performUserAction(any(), any())).thenReturn(Completable.complete());

        Policy policy = mock(Policy.class);
        when(policy.policyInst()).thenReturn(userActionPolicy);

        when(flowManager.findByExtensionPoint(eq(extensionPoint), eq(client), any()))
                .thenReturn(Single.just(List.of(policy)));

        // Execution context required by the endpoint, we only need attributes map
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getAttributes()).thenReturn(new HashMap<>());
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        when(environment.getProperty(anyString(), any(), anyLong())).thenReturn(18000L);

        UserActionEndpoint endpoint = new UserActionEndpoint(templateEngine, flowManager, executionContextFactory, jwtService, domain, environment);
        router.route(HttpMethod.POST, "/userAction")
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create())
                .handler(routingContext -> {
                    routingContext.put(CLIENT_CONTEXT_KEY, client);
                    routingContext.next();
                })
                .handler(endpoint)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.POST, "/userAction?ua_state=state-token",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains(expectedPath));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
