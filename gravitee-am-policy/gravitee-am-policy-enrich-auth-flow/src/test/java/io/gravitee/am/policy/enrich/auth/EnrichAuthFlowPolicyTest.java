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
package io.gravitee.am.policy.enrich.auth;

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.policy.enrich.auth.configuration.EnrichAuthFlowPolicyConfiguration;
import io.gravitee.am.policy.enrich.auth.configuration.Property;
import io.gravitee.am.repository.management.api.AuthenticationFlowContextRepository;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.Maps;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.el.TemplateEngine;
import io.gravitee.el.spel.SpelTemplateEngineFactory;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.RequestWrapper;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.reactivex.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class EnrichAuthFlowPolicyTest {

    public static final String PARAM_VALUE = "Content";
    public static final String REQUEST_PARAM = "param";

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private Request request;

    @Mock
    private Response response;

    private PolicyChain policyChain;

    @Mock
    private EnrichAuthFlowPolicyConfiguration configuration;

    @Mock
    private AuthenticationFlowContextRepository authContextRepository;

    @Mock
    private Environment environment;

    @Before
    public void init() {
        reset(configuration, executionContext, request, response, authContextRepository);

        Request request = new RequestWrapper(mock(Request.class)) {
            @Override
            public MultiValueMap<String, String> parameters() {
                LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
                parameters.add(REQUEST_PARAM, PARAM_VALUE);
                return parameters;
            }
        };

        TemplateEngine tplEngine = new SpelTemplateEngineFactory().templateEngine();
        tplEngine.getTemplateContext().setVariable("request", new EvaluableRequest(request));


        when(executionContext.getTemplateEngine()).thenReturn(tplEngine);
        when(executionContext.getComponent(AuthenticationFlowContextRepository.class)).thenReturn(this.authContextRepository);
        when(environment.getProperty("authenticationFlow.expirationTimeOut", Integer.class, 300)).thenReturn(300);
        when(executionContext.getComponent(Environment.class)).thenReturn(environment);
    }

    private void mockGraviteeContext(int version, Map<String, Object> data) {
        AuthenticationFlowContext authenticationFlowContext = new AuthenticationFlowContext();
        authenticationFlowContext.setData(data);
        authenticationFlowContext.setVersion(version);
        when(executionContext.getAttribute(ConstantKeys.AUTH_FLOW_CONTEXT_KEY)).thenReturn(authenticationFlowContext);
    }

    @Test
    public void shouldStoreData() throws Exception {
        final CountDownLatch lock = new CountDownLatch(1);
        this.policyChain = spy(new CountDownPolicyChain(lock));

        final int expectedVersion = 2;
        mockGraviteeContext(expectedVersion - 1, Maps.<String, Object>builder().put("entry1", "value1").build());
        when(configuration.getProperties()).thenReturn(Arrays.asList(
                new Property("key", "myValue"),
                new Property("key-tpl", "{#request.params['"+REQUEST_PARAM+"']}")));

        when(authContextRepository.create(any())).then((arg) -> Single.just(arg.getArgument(0)));

        EnrichAuthFlowPolicy enrichAuthFlowPolicy = buildPolicy();
        enrichAuthFlowPolicy.onRequest(request, response, executionContext, policyChain);

        lock.await(1, TimeUnit.SECONDS);
        verify(policyChain, never()).failWith(any());
        verify(policyChain).doNext(any(), any());
        verify(authContextRepository).create(argThat(authContext ->
        {
            return authContext.getVersion() == expectedVersion && // version is incremented
            authContext.getData() != null &&
            "value1".equals(authContext.getData().get("entry1")) &&
            "myValue".equals(authContext.getData().get("key")) &&
            PARAM_VALUE.equals(authContext.getData().get("key-tpl"));
        }));
    }

    @Test
    public void shouldIgnoreContext_NoGraviteeContext() throws Exception {
        final CountDownLatch lock = new CountDownLatch(1);
        this.policyChain = spy(new CountDownPolicyChain(lock));

        when(configuration.getProperties()).thenReturn(Arrays.asList(
                new Property("key", "myValue"),
                new Property("key-tpl", "{#request.params['"+REQUEST_PARAM+"']}")));

        EnrichAuthFlowPolicy enrichAuthFlowPolicy = buildPolicy();
        enrichAuthFlowPolicy.onRequest(request, response, executionContext, policyChain);

        lock.await(1, TimeUnit.SECONDS);
        verify(policyChain, never()).failWith(any());
        verify(policyChain).doNext(any(), any());
        verify(authContextRepository, never()).create(any());
    }

    @Test
    public void shouldIgnoreContext_NoConfigProperties() throws Exception {
        final CountDownLatch lock = new CountDownLatch(1);
        this.policyChain = spy(new CountDownPolicyChain(lock));

        EnrichAuthFlowPolicy enrichAuthFlowPolicy = buildPolicy();
        enrichAuthFlowPolicy.onRequest(request, response, executionContext, policyChain);

        lock.await(1, TimeUnit.SECONDS);
        verify(policyChain, never()).failWith(any());
        verify(policyChain).doNext(any(), any());
        verify(authContextRepository, never()).create(any());
    }

    private EnrichAuthFlowPolicy buildPolicy() {
        EnrichAuthFlowPolicy enrichAuthFlowPolicy = new EnrichAuthFlowPolicy(configuration);
        return enrichAuthFlowPolicy;
    }

    class CountDownPolicyChain implements PolicyChain {
        private final CountDownLatch lock;

        public CountDownPolicyChain(CountDownLatch lock) {
            this.lock = lock;
        }

        @Override
        public void doNext(Request request, Response response) {
            lock.countDown();
        }

        @Override
        public void failWith(PolicyResult policyResult) {
            lock.countDown();
        }

        @Override
        public void streamFailWith(PolicyResult policyResult) {
            lock.countDown();
        }
    }
}
