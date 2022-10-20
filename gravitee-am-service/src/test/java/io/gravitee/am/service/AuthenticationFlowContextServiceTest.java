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

import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.repository.management.api.AuthenticationFlowContextRepository;
import io.gravitee.am.service.exception.AuthenticationFlowConsistencyException;
import io.gravitee.am.service.impl.AuthenticationFlowContextServiceImpl;
import io.gravitee.am.service.utils.SetterUtilsTest;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationFlowContextServiceTest {
    private static final String SESSION_ID = "someid";

    @InjectMocks
    private AuthenticationFlowContextServiceImpl service;

    @Mock
    private AuthenticationFlowContextRepository authFlowContextRepository;

    @Test
    public void testLoadContext_UnknownSessionId() {
        // if sessionId is unknown load default Context
        when(authFlowContextRepository.findLastByTransactionId(any())).thenReturn(Maybe.empty());

        TestObserver<AuthenticationFlowContext> testObserver = service.loadContext(SESSION_ID, 1).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(ctx -> ctx.getVersion() == 0);
        verify(authFlowContextRepository).findLastByTransactionId(any());
    }

    @Test
    public void testLoadContext() {
        AuthenticationFlowContext context = new AuthenticationFlowContext();
        context.setVersion(1);
        context.setTransactionId(SESSION_ID);

        // if sessionId is unknown load default Context
        when(authFlowContextRepository.findLastByTransactionId(any())).thenReturn(Maybe.just(context));

        TestObserver<AuthenticationFlowContext> testObserver = service.loadContext(SESSION_ID, 1).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(ctx -> ctx.getVersion() == 1);
        testObserver.assertValue(ctx -> ctx.getTransactionId().equals(SESSION_ID));
        verify(authFlowContextRepository).findLastByTransactionId(any());
    }

    @Test
    public void testLoadContext_ConsistencyError() {
        AuthenticationFlowContext context = new AuthenticationFlowContext();
        context.setVersion(1);
        context.setTransactionId(SESSION_ID);

        // if sessionId is unknown load default Context
        when(authFlowContextRepository.findLastByTransactionId(any())).thenReturn(Maybe.just(context));

        TestObserver<AuthenticationFlowContext> testObserver = service.loadContext(SESSION_ID, 2).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertError(error -> error instanceof AuthenticationFlowConsistencyException);
        verify(authFlowContextRepository).findLastByTransactionId(any());
    }

    @Test
    public void testClearContext() {
        when(authFlowContextRepository.delete(SESSION_ID)).thenReturn(Completable.complete());
        TestObserver<Void> testObserver = service.clearContext(SESSION_ID).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        verify(authFlowContextRepository).delete(SESSION_ID);
    }

    @Test
    public void testClearContext_NullId() {
        TestObserver<Void> testObserver = service.clearContext(null).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        verify(authFlowContextRepository, never()).delete(any());
    }

    @Test
    public void testUpdateContext() {
        ReflectionTestUtils.setField(this.service, "contextExpiration", 300);
        when(this.authFlowContextRepository.create(any())).thenReturn(Single.just(new AuthenticationFlowContext()));
        final AuthenticationFlowContext authContext = new AuthenticationFlowContext();
        authContext.setVersion(1);
        final Date now = new Date();
        authContext.setExpireAt(now);
        final TestObserver<AuthenticationFlowContext> testObserver = service.updateContext(authContext).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertValueCount(1);

        verify(this.authFlowContextRepository).create(argThat(ctx -> ctx.getVersion() == 2
                && ctx.getExpireAt().after(now) ));
    }
}
