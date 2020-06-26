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
package io.gravitee.am.gateway.handler.uma.policy;

import io.gravitee.am.gateway.handler.common.policy.PolicyManager;
import io.gravitee.am.gateway.policy.PolicyChainProcessorFactory;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

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
    private PolicyManager policyManager;

    @Mock
    private ExecutionContext executionContext;

    @Test
    public void shouldNotInvoke_noPolicies() {
        TestObserver testObserver = rulesEngine.fire(Collections.emptyList(), executionContext).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete().assertNoErrors();
        verify(policyManager, never()).create(anyString(), anyString());
        verify(policyChainProcessorFactory, never()).create(any(), any());
    }
}
