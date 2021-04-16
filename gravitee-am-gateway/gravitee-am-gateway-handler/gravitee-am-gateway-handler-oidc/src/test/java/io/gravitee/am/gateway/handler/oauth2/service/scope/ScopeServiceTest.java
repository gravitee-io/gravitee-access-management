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
package io.gravitee.am.gateway.handler.oauth2.service.scope;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.gravitee.am.gateway.handler.oauth2.service.scope.impl.ScopeServiceImpl;
import io.gravitee.am.model.oauth2.Scope;
import io.reactivex.observers.TestObserver;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ScopeServiceTest {

    @InjectMocks
    private ScopeService scopeService = new ScopeServiceImpl();

    @Mock
    private ScopeManager scopeManager;

    @Before
    public void setUp() {
        Scope one = new Scope();
        one.setKey("one");

        Scope two = new Scope();
        two.setKey("two");
        two.setDiscovery(true);

        when(scopeManager.findAll()).thenReturn(new HashSet<>(Arrays.asList(one, two)));
    }

    @Test
    public void getAll() {
        TestObserver testObserver = scopeService.getAll().test();

        verify(scopeManager, times(1)).findAll();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(list -> ((Set) list).size() == 2);
    }

    @Test
    public void getDiscoveryScope() {
        assertEquals(1, scopeService.getDiscoveryScope().size());
    }
}
