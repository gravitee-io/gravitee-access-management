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
package io.gravitee.am.gateway.handler.account.services;

import io.gravitee.am.gateway.handler.account.services.impl.AccountServiceImpl;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Credential;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CredentialService;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AccountServiceTest {

    @Mock
    private CredentialService credentialService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AccountService accountService = new AccountServiceImpl();

    @Test
    public void shouldRemoveWebAuthnCredentials_nominalCase() {
        final String userId = "user-id";
        final String credentialId = "credential-id";
        final User principal = new DefaultUser();
        final Credential credential = mock(Credential.class);
        when(credential.getId()).thenReturn(credentialId);
        when(credential.getUserId()).thenReturn("user-id");

        when(credentialService.findById(credentialId)).thenReturn(Maybe.just(credential));
        when(credentialService.delete(credentialId)).thenReturn(Completable.complete());

        TestObserver testObserver = accountService.removeWebAuthnCredential(userId, credentialId, principal).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, times(1)).findById(credentialId);
        verify(credentialService, times(1)).delete(credentialId);
        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldRemoveWebAuthnCredentials_notFound() {
        final String userId = "user-id";
        final String credentialId = "credential-id";
        final User principal = new DefaultUser();

        when(credentialService.findById(credentialId)).thenReturn(Maybe.empty());

        TestObserver testObserver = accountService.removeWebAuthnCredential(userId, credentialId, principal).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, times(1)).findById(credentialId);
        verify(credentialService, never()).delete(credentialId);
        verify(auditService, never()).report(any());
    }

    @Test
    public void shouldRemoveWebAuthnCredentials_notTheSameUser() {
        final String userId = "user-id";
        final String credentialId = "credential-id";
        final User principal = new DefaultUser();
        final Credential credential = mock(Credential.class);
        when(credential.getUserId()).thenReturn("unknown-user-id");

        when(credentialService.findById(credentialId)).thenReturn(Maybe.just(credential));

        TestObserver testObserver = accountService.removeWebAuthnCredential(userId, credentialId, principal).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, times(1)).findById(credentialId);
        verify(credentialService, never()).delete(credentialId);
        verify(auditService, never()).report(any());
    }
}
