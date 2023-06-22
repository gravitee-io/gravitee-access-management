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

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.gateway.policy.PolicyChainProcessorFactory;
import io.gravitee.am.gateway.policy.impl.PolicyChain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.plugins.policy.core.PolicyPluginManager;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RulesEngineTest {

    @InjectMocks
    private RulesEngine rulesEngine = new DefaultRulesEngine();

    @Mock
    private PolicyChainProcessorFactory policyChainProcessorFactory;

    @Mock
    private PolicyPluginManager policyPluginManager;
    @Mock
    private ExecutionContext executionContext;

    @Mock
    private FlowManager flowManager;

    @Mock
    private ExecutionContextFactory executionContextFactory;

    @Test
    public void shouldNotInvoke_noRules() {
        TestObserver testObserver = rulesEngine.fire(Collections.emptyList(), executionContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete().assertNoErrors();
        verify(policyPluginManager, never()).create(anyString(), anyString());
        verify(policyChainProcessorFactory, never()).create(any(), any());
    }

    @Test
    public void shouldNotInvoke_oneRule() {
        Rule rule = Mockito.mock(Rule.class);
        when(rule.enabled()).thenReturn(true);
        when(rule.type()).thenReturn("type");
        when(rule.condition()).thenReturn("condition");
        Policy policy = mock(Policy.class);
        PolicyChain policyChain = new PolicyChain(List.of(policy), executionContext);
        when(policyPluginManager.create(any(), any())).thenReturn(policy);
        when(policyChainProcessorFactory.create(any(), any())).thenReturn(policyChain);

        TestObserver testObserver = rulesEngine.fire(List.of(rule), executionContext).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete().assertNoErrors();
        verify(policyPluginManager, times(1)).create(anyString(), anyString());
        verify(policyChainProcessorFactory, times(1)).create(any(), any());
    }

    @Test
    public void shouldNotInvoke_noPolicies() {
        Request request = Mockito.mock(Request.class);
        Client client = mock(Client.class);
        User user = mock(User.class);
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        when(flowManager.findByExtensionPoint(any(), any(), any())).thenReturn(Single.just(Collections.emptyList()));

        TestObserver testObserver = rulesEngine.fire(ExtensionPoint.PRE_TOKEN, request, client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete().assertNoErrors();
        verify(policyChainProcessorFactory, never()).create(any(), any());
    }

    @Test
    public void shouldInvoke_onePolicy() {
        Request request = Mockito.mock(Request.class);
        Client client = mock(Client.class);
        User user = mock(User.class);
        Policy policy = mock(Policy.class);
        PolicyChain policyChain = new PolicyChain(List.of(policy), executionContext);
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        when(flowManager.findByExtensionPoint(any(), any(), any())).thenReturn(Single.just(List.of(policy)));
        when(policyChainProcessorFactory.create(any(), any())).thenReturn(policyChain);

        TestObserver testObserver = rulesEngine.fire(ExtensionPoint.PRE_TOKEN, request, client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete().assertNoErrors();
        verify(policyChainProcessorFactory, times(1)).create(any(), any());
    }
}
