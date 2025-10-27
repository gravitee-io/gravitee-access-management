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

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.core.processor.Processor;
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.handler.common.flow.ExecutionPredicate;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.PolicyChainHandlerImpl;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.gateway.policy.PolicyChainProcessorFactory;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.apache.shiro.crypto.hash.Hash;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PolicyChainHandlerTest {

    @Mock
    private FlowManager flowManager;

    @Mock
    private PolicyChainProcessorFactory policyChainProcessorFactory;

    @Mock
    private ExecutionContextFactory executionContextFactory;

    @Mock
    private RoutingContext routingContext;

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private HttpServerRequest request;

    @Mock
    private io.vertx.core.http.HttpServerRequest delegateRequest;

    @Mock
    private io.vertx.core.http.HttpServerResponse delegateResponse;

    @Mock
    private Policy policy;

    @Mock
    private Processor<ExecutionContext> processor;

    private boolean skipAllFlowsOnError = false;

    @Test
    public void shouldNotInvoke_noPolicies() {
        when(flowManager.findByExtensionPoint(eq(ExtensionPoint.PRE_CONSENT), eq(null), any(ExecutionPredicate.class))).thenReturn(Single.just(Collections.emptyList()));
        when(delegateRequest.method()).thenReturn(HttpMethod.GET);
        when(request.getDelegate()).thenReturn(delegateRequest);
        when(request.getDelegate().response()).thenReturn(delegateResponse);
        when(routingContext.request()).thenReturn(request);
        when(executionContext.getAttributes()).thenReturn(Collections.emptyMap());
        when(executionContextFactory.create(any())).thenReturn(executionContext);

        PolicyChainHandlerImpl policyChainHandler = new PolicyChainHandlerImpl(flowManager, policyChainProcessorFactory, executionContextFactory, ExtensionPoint.PRE_CONSENT, skipAllFlowsOnError);

        when(routingContext.request()).thenReturn(request);
        policyChainHandler.handle(routingContext);

        verify(flowManager, times(1)).findByExtensionPoint(eq(ExtensionPoint.PRE_CONSENT), eq(null), any(ExecutionPredicate.class));
        verify(policyChainProcessorFactory, never()).create(any(), any());
        verify(executionContextFactory).create(any());
    }

    @Test
    public void shouldNotInvokePolicy_GET_Method_with_error_param() {
        when(request.method()).thenReturn(HttpMethod.GET);
        MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
        multiMap.add("error", "any_error");
        when(request.params()).thenReturn(multiMap);
        when(routingContext.request()).thenReturn(request);

        PolicyChainHandlerImpl policyChainHandler = new PolicyChainHandlerImpl(flowManager, policyChainProcessorFactory, executionContextFactory, ExtensionPoint.PRE_CONSENT, skipAllFlowsOnError);

        policyChainHandler.handle(routingContext);

        verify(flowManager, never()).findByExtensionPoint(eq(ExtensionPoint.PRE_CONSENT), eq(null), any(ExecutionPredicate.class));
        verify(policyChainProcessorFactory, never()).create(any(), any());
        verify(executionContextFactory, never()).create(any());
    }

    @Test
    public void shouldNotInvokePolicy_POST_Method_with_error_param_BUT_skipAllFlowsOnError_is_true() {
        lenient().when(request.method()).thenReturn(HttpMethod.POST);
        MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
        multiMap.add("error", "any_error");
        when(request.params()).thenReturn(multiMap);
        when(routingContext.request()).thenReturn(request);

        PolicyChainHandlerImpl policyChainHandler = new PolicyChainHandlerImpl(flowManager, policyChainProcessorFactory, executionContextFactory, ExtensionPoint.POST_CONSENT, true);

        policyChainHandler.handle(routingContext);

        verify(flowManager, never()).findByExtensionPoint(eq(ExtensionPoint.POST_CONSENT), eq(null), any(ExecutionPredicate.class));
        verify(policyChainProcessorFactory, never()).create(any(), any());
        verify(executionContextFactory, never()).create(any());
    }

    @Test
    public void shouldInvokePolicy_POST_Method_with_error_param() {
        when(flowManager.findByExtensionPoint(eq(ExtensionPoint.POST_CONSENT), eq(null), any(ExecutionPredicate.class))).thenReturn(Single.just(Collections.singletonList(policy)));
        when(delegateRequest.method()).thenReturn(HttpMethod.POST);
        when(request.method()).thenReturn(HttpMethod.POST);
        when(request.getDelegate()).thenReturn(delegateRequest);
        when(request.getDelegate().response()).thenReturn(delegateResponse);
        when(routingContext.request()).thenReturn(request);
        when(executionContext.getAttributes()).thenReturn(Collections.emptyMap());
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        when(processor.handler(any())).thenReturn(processor);
        when(processor.errorHandler(any())).thenReturn(processor);
        when(policyChainProcessorFactory.create(Collections.singletonList(policy), executionContext)).thenReturn(processor);

        PolicyChainHandlerImpl policyChainHandler = new PolicyChainHandlerImpl(flowManager, policyChainProcessorFactory, executionContextFactory, ExtensionPoint.POST_CONSENT, skipAllFlowsOnError);

        policyChainHandler.handle(routingContext);

        verify(flowManager, times(1)).findByExtensionPoint(eq(ExtensionPoint.POST_CONSENT), eq(null), any(ExecutionPredicate.class));
        verify(policyChainProcessorFactory, times(1)).create(any(), any());
        verify(executionContextFactory, times(1)).create(any());
    }

    @Test
    public void shouldInvoke_onePolicy() {
        when(flowManager.findByExtensionPoint(eq(ExtensionPoint.PRE_CONSENT), eq(null), any(ExecutionPredicate.class))).thenReturn(Single.just(Collections.singletonList(policy)));
        when(delegateRequest.method()).thenReturn(HttpMethod.GET);
        when(request.getDelegate()).thenReturn(delegateRequest);
        when(request.getDelegate().response()).thenReturn(delegateResponse);
        when(routingContext.request()).thenReturn(request);
        when(executionContext.getAttributes()).thenReturn(Collections.emptyMap());
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        when(processor.handler(any())).thenReturn(processor);
        when(processor.errorHandler(any())).thenReturn(processor);
        when(policyChainProcessorFactory.create(Collections.singletonList(policy), executionContext)).thenReturn(processor);

        PolicyChainHandlerImpl policyChainHandler = new PolicyChainHandlerImpl(flowManager, policyChainProcessorFactory, executionContextFactory, ExtensionPoint.PRE_CONSENT, skipAllFlowsOnError);

        policyChainHandler.handle(routingContext);

        verify(flowManager, times(1)).findByExtensionPoint(eq(ExtensionPoint.PRE_CONSENT), eq(null), any(ExecutionPredicate.class));
        verify(policyChainProcessorFactory, times(1)).create(any(), any());
        verify(executionContextFactory, times(1)).create(any());
    }

    @Test
    public void shouldInvoke_manyPolicies() {
        when(flowManager.findByExtensionPoint(eq(ExtensionPoint.PRE_CONSENT), eq(null), any(ExecutionPredicate.class))).thenReturn(Single.just(Arrays.asList(policy, policy)));
        when(delegateRequest.method()).thenReturn(HttpMethod.GET);
        when(request.getDelegate()).thenReturn(delegateRequest);
        when(request.getDelegate().response()).thenReturn(delegateResponse);
        when(routingContext.request()).thenReturn(request);
        when(executionContext.getAttributes()).thenReturn(Collections.emptyMap());
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        when(processor.handler(any())).thenReturn(processor);
        when(processor.errorHandler(any())).thenReturn(processor);
        when(policyChainProcessorFactory.create(Arrays.asList(policy, policy), executionContext)).thenReturn(processor);

        PolicyChainHandlerImpl policyChainHandler = new PolicyChainHandlerImpl(flowManager, policyChainProcessorFactory, executionContextFactory, ExtensionPoint.PRE_CONSENT, skipAllFlowsOnError);

        policyChainHandler.handle(routingContext);

        // should be only call once
        verify(flowManager, times(1)).findByExtensionPoint(eq(ExtensionPoint.PRE_CONSENT), eq(null), any(ExecutionPredicate.class));
        verify(policyChainProcessorFactory, times(1)).create(any(), any());
        verify(executionContextFactory, times(1)).create(any());
    }
}
