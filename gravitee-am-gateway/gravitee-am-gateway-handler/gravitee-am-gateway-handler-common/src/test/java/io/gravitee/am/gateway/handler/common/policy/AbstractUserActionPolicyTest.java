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
package io.gravitee.am.gateway.handler.common.policy;

import io.gravitee.common.http.HttpMethod;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractUserActionPolicyTest {

    @Mock
    private ExecutionContext context;

    @Mock
    private Request request;

    @Mock
    private Response response;

    @Mock
    private PolicyChain policyChain;

    @Mock
    private Client client;

    @Mock
    private JWTService jwtService;

    private final TestUserActionPolicy policy = new TestUserActionPolicy();

    @Test
    public void shouldFail_whenRequestMethodIsNotPost() {
        when(request.method()).thenReturn(HttpMethod.GET);

        policy.onRequest(request, response, context, policyChain);

        ArgumentCaptor<PolicyResult> captor = ArgumentCaptor.forClass(PolicyResult.class);
        verify(policyChain).failWith(captor.capture());

        PolicyResult result = captor.getValue();
        assertThat(result).isNotNull();
        assertThat(result.key()).isEqualTo("GATEWAY_POLICY_USER_ACTION_ERROR");
        assertThat(result.message()).isEqualTo("User Action policy requires POST method");
    }

    @Test
    public void shouldFail_whenFlowIsNotDetected() {
        when(request.method()).thenReturn(HttpMethod.POST);
        when(request.uri()).thenReturn("/something");

        policy.onRequest(request, response, context, policyChain);

        ArgumentCaptor<PolicyResult> captor = ArgumentCaptor.forClass(PolicyResult.class);
        verify(policyChain).failWith(captor.capture());

        PolicyResult result = captor.getValue();
        assertThat(result).isNotNull();
        assertThat(result.key()).isEqualTo("GATEWAY_POLICY_USER_ACTION_ERROR");
        assertThat(result.message()).isEqualTo("User Action policy requires a compatible FLOW");
    }

    @Test
    public void shouldFail_whenJwtServiceIsUnavailable() {
        when(request.method()).thenReturn(HttpMethod.POST);
        when(request.uri()).thenReturn(Template.LOGIN.redirectUri() + "?foo=bar");
        when(context.getAttribute(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(context.getComponent(JWTService.class)).thenReturn(null);

        policy.onRequest(request, response, context, policyChain);

        ArgumentCaptor<PolicyResult> captor = ArgumentCaptor.forClass(PolicyResult.class);
        verify(policyChain).failWith(captor.capture());

        PolicyResult result = captor.getValue();
        assertThat(result).isNotNull();
        assertThat(result.key()).isEqualTo("GATEWAY_POLICY_USER_ACTION_ERROR");
        assertThat(result.message()).isEqualTo("JWTService is unavailable");
    }

    @Test
    public void shouldRedirect_whenFlowIsDetected() {
        when(request.method()).thenReturn(HttpMethod.POST);
        when(request.uri()).thenReturn(Template.LOGIN.redirectUri() + "?foo=bar");
        when(context.getAttribute(ConstantKeys.CLIENT_CONTEXT_KEY)).thenReturn(client);
        when(context.getAttribute("contextPath")).thenReturn("/my-context");
        when(context.getComponent(JWTService.class)).thenReturn(jwtService);
        when(client.getDomain()).thenReturn("my-domain");
        when(client.getClientId()).thenReturn("my-client");
        when(jwtService.encode(any(), eq(client))).thenReturn(Single.just("encoded-state"));

        // Stub request properties used by UriBuilderRequest
        when(request.version()).thenReturn(io.gravitee.common.http.HttpVersion.HTTP_1_1);
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("localhost:8080");
        when(request.path()).thenReturn(Template.LOGIN.redirectUri());
        io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpHeaders httpHeaders =
                new io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpHeaders(io.vertx.core.MultiMap.caseInsensitiveMultiMap());
        when(request.headers()).thenReturn(httpHeaders);
        when(request.parameters()).thenReturn(httpHeaders);

        policy.onRequest(request, response, context, policyChain);

        ArgumentCaptor<PolicyResult> captor = ArgumentCaptor.forClass(PolicyResult.class);
        verify(policyChain).failWith(captor.capture());

        PolicyResult result = captor.getValue();
        assertThat(result).isNotNull();
        assertThat(result.key()).isEqualTo("GATEWAY_POLICY_USER_ACTION_ERROR");
        assertThat(result.statusCode()).isEqualTo(302);
        assertThat(result.message()).isEqualTo("User Action is required");
        Map<String, Object> parameters = result.parameters();
        assertThat(parameters).containsKey(ConstantKeys.RETURN_URL_KEY);
        assertThat(String.valueOf(parameters.get(ConstantKeys.RETURN_URL_KEY))).contains("/userAction");
        assertThat(String.valueOf(parameters.get(ConstantKeys.RETURN_URL_KEY))).contains("ua_state=encoded-state");
    }

    private static class TestUserActionPolicy extends AbstractUserActionPolicy {

        @Override
        public io.reactivex.rxjava3.core.Completable performUserAction(Request request, ExecutionContext context) {
            return io.reactivex.rxjava3.core.Completable.complete();
        }

        @Override
        public String getAction() {
            return "test";
        }

        @Override
        public String getTemplate() {
            return "template";
        }
    }
}
