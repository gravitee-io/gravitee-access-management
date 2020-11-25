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
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.PolicyChainHandlerImpl;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.gateway.policy.PolicyChainProcessorFactory;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

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
    private Policy policy;

    @Mock
    private Processor<ExecutionContext> processor;

    @Test
    public void shouldNotInvoke_noPolicies() {
        when(flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT)).thenReturn(Single.just(Collections.emptyList()));

        PolicyChainHandlerImpl policyChainHandler = new PolicyChainHandlerImpl(flowManager, policyChainProcessorFactory, executionContextFactory, ExtensionPoint.PRE_CONSENT);

        policyChainHandler.handle(routingContext);

        verify(flowManager, times(1)).findByExtensionPoint(ExtensionPoint.PRE_CONSENT);
        verify(policyChainProcessorFactory, never()).create(any(), any());
        verify(executionContextFactory, never()).create(any());
    }

    @Test
    public void shouldInvoke_onePolicy() {
        when(flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT)).thenReturn(Single.just(Collections.singletonList(policy)));
        when(delegateRequest.method()).thenReturn(HttpMethod.GET);
        when(request.getDelegate()).thenReturn(delegateRequest);
        when(routingContext.request()).thenReturn(request);
        when(executionContext.getAttributes()).thenReturn(Collections.emptyMap());
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        when(processor.handler(any())).thenReturn(processor);
        when(processor.errorHandler(any())).thenReturn(processor);
        when(policyChainProcessorFactory.create(Collections.singletonList(policy), executionContext)).thenReturn(processor);

        PolicyChainHandlerImpl policyChainHandler = new PolicyChainHandlerImpl(flowManager, policyChainProcessorFactory, executionContextFactory, ExtensionPoint.PRE_CONSENT);

        policyChainHandler.handle(routingContext);

        verify(flowManager, times(1)).findByExtensionPoint(ExtensionPoint.PRE_CONSENT);
        verify(policyChainProcessorFactory, times(1)).create(any(), any());
        verify(executionContextFactory, times(1)).create(any());
    }

    @Test
    public void shouldInvoke_manyPolicies() {
        when(flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT)).thenReturn(Single.just(Arrays.asList(policy, policy)));
        when(delegateRequest.method()).thenReturn(HttpMethod.GET);
        when(request.getDelegate()).thenReturn(delegateRequest);
        when(routingContext.request()).thenReturn(request);
        when(executionContext.getAttributes()).thenReturn(Collections.emptyMap());
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        when(processor.handler(any())).thenReturn(processor);
        when(processor.errorHandler(any())).thenReturn(processor);
        when(policyChainProcessorFactory.create(Arrays.asList(policy, policy), executionContext)).thenReturn(processor);

        PolicyChainHandlerImpl policyChainHandler = new PolicyChainHandlerImpl(flowManager, policyChainProcessorFactory, executionContextFactory, ExtensionPoint.PRE_CONSENT);

        policyChainHandler.handle(routingContext);

        // should be only call once
        verify(flowManager, times(1)).findByExtensionPoint(ExtensionPoint.PRE_CONSENT);
        verify(policyChainProcessorFactory, times(1)).create(any(), any());
        verify(executionContextFactory, times(1)).create(any());
    }
}
