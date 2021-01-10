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
package io.gravitee.am.policy.enrich.profile;

import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.model.User;
import io.gravitee.am.policy.enrich.profile.configuration.EnrichProfilePolicyConfiguration;
import io.gravitee.am.policy.enrich.profile.configuration.Property;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.common.util.LinkedMultiValueMap;
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

import java.util.Arrays;
import java.util.HashMap;
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
public class EnrichProfilePolicyTest {

    public static final String PARAM_VALUE = "Error Content";
    public static final String REQUEST_PARAM = "param";
    @Mock
    private ExecutionContext executionContext;

    @Mock
    private Request request;

    @Mock
    private Response response;

    private PolicyChain policyChain;

    @Mock
    private EnrichProfilePolicyConfiguration configuration;

    @Mock
    private UserRepository userRepository;

    @Before
    public void init() {
        reset(configuration, executionContext, request, response);

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
        when(executionContext.getComponent(UserRepository.class)).thenReturn(userRepository);
    }

    @Test
    public void shouldIgnoreError() throws Exception {
        final CountDownLatch lock = new CountDownLatch(1);
        this.policyChain = spy(new CountDownPolicyChain(lock));

        new EnrichProfilePolicy(configuration){
            @Override
            protected boolean prepareUserProfile(ExecutionContext context) {
                throw new RuntimeException("Exception thrown for test");
            }
        }.onRequest(request, response, executionContext, policyChain);

        lock.await(1, TimeUnit.SECONDS);
        verify(policyChain, never()).failWith(any());
        verify(policyChain).doNext(any(), any());
    }

    @Test
    public void shouldFailOnError() throws Exception {
        when(configuration.isExitOnError()).thenReturn(true);

        final CountDownLatch lock = new CountDownLatch(1);
        this.policyChain = spy(new CountDownPolicyChain(lock));

        new EnrichProfilePolicy(configuration){
            @Override
            protected boolean prepareUserProfile(ExecutionContext context) {
                throw new RuntimeException("Exception thrown for test");
            }
        }.onRequest(request, response, executionContext, policyChain);

        lock.await(1, TimeUnit.SECONDS);
        verify(policyChain).failWith(argThat(result -> result.message().equals(EnrichProfilePolicy.errorMessage)));
        verify(policyChain, never()).doNext(any(), any());
    }


    @Test
    public void shouldFailOnError_InRxFlow() throws Exception {
        when(configuration.isExitOnError()).thenReturn(true);

        final CountDownLatch lock = new CountDownLatch(1);
        this.policyChain = spy(new CountDownPolicyChain(lock));

        User user = mock(User.class);
        when(executionContext.getAttribute("user")).thenReturn(user);

        when(userRepository.update(any())).thenReturn(Single.error(new RuntimeException("Exception thrown for test")));


        new EnrichProfilePolicy(configuration){
            @Override
            protected boolean prepareUserProfile(ExecutionContext context) {
                return true; // go to RxFlow
            }
        }.onRequest(request, response, executionContext, policyChain);

        lock.await(1, TimeUnit.SECONDS);
        verify(policyChain).failWith(argThat(result -> result.message().equals(EnrichProfilePolicy.errorMessage)));
        verify(policyChain, never()).doNext(any(), any());
    }

    @Test
    public void shouldIgnoreUserUpdate_NoProperties() throws Exception {
        final CountDownLatch lock = new CountDownLatch(1);
        this.policyChain = spy(new CountDownPolicyChain(lock));

        new EnrichProfilePolicy(configuration).onRequest(request, response, executionContext, policyChain);

        lock.await(1, TimeUnit.SECONDS);
        verify(policyChain, never()).failWith(any());
        verify(policyChain).doNext(any(), any());
        verify(userRepository, never()).update(any());
    }

    @Test
    public void shouldUpdateUser() throws Exception {
        final CountDownLatch lock = new CountDownLatch(1);
        this.policyChain = spy(new CountDownPolicyChain(lock));

        when(configuration.getProperties()).thenReturn(Arrays.asList(
                new Property("myclaim", "myclaimValue"),
                new Property("myclaim-tpl", "{#request.params['"+REQUEST_PARAM+"']}")));

        User user = mock(User.class);
        Map<String, Object> additionalInformation = new HashMap<>();
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);
        when(executionContext.getAttribute("user")).thenReturn(user);

        when(userRepository.update(any())).thenReturn(Single.just(user));

        EnrichProfilePolicy enrichProfilePolicy = new EnrichProfilePolicy(configuration);
        enrichProfilePolicy.onRequest(request, response, executionContext, policyChain);

        lock.await(1, TimeUnit.SECONDS);
        verify(policyChain, never()).failWith(any());
        verify(policyChain).doNext(any(), any());
        verify(userRepository).update(argThat(u ->
                u.getAdditionalInformation() != null &&
                u.getAdditionalInformation().containsKey("myclaim") &&
                "myclaimValue".equals(u.getAdditionalInformation().get("myclaim")) &&
                u.getAdditionalInformation().containsKey("myclaim-tpl") &&
                PARAM_VALUE.equals(u.getAdditionalInformation().get("myclaim-tpl"))));
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
