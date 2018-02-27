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
package io.gravitee.am.service;

import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.ScopeApprovalServiceImpl;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collection;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ScopeApprovalServiceTest {

    @InjectMocks
    private ScopeApprovalService scopeApprovalService = new ScopeApprovalServiceImpl();

    @Mock
    private ScopeApprovalRepository scopeApprovalRepository;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldCreate() {
        ScopeApproval scopeApproval = Mockito.mock(ScopeApproval.class);
        when(scopeApprovalRepository.create(any(ScopeApproval.class))).thenReturn(Single.just(new ScopeApproval()));

        TestObserver testObserver = scopeApprovalService.create(scopeApproval).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(scopeApprovalRepository, times(1)).create(any(ScopeApproval.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        ScopeApproval scopeApproval = Mockito.mock(ScopeApproval.class);
        when(scopeApprovalRepository.create(any(ScopeApproval.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver<ScopeApproval> testObserver = new TestObserver<>();
        scopeApprovalService.create(scopeApproval).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByUserAndClient() {
        when(scopeApprovalRepository.findByDomainAndUserAndClient(DOMAIN, "user-1", "client-1")).thenReturn(Single.just(Collections.singleton(new ScopeApproval())));
        TestObserver<Collection<ScopeApproval>> testObserver = scopeApprovalService.findByUserAndClient(DOMAIN, "user-1", "client-1").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopeApprovals -> scopeApprovals.size() == 1);
    }

    @Test
    public void shouldFindByUserAndClient_technicalException() {
        when(scopeApprovalRepository.findByDomainAndUserAndClient(DOMAIN, "user-1", "client-1")).thenReturn(Single.error(TechnicalException::new));

        TestObserver<Collection<ScopeApproval>> testObserver = new TestObserver<>();
        scopeApprovalService.findByUserAndClient(DOMAIN, "user-1", "client-1").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }


}
