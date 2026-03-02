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

package io.gravitee.am.gateway.handler.common.service;


import io.gravitee.am.gateway.handler.common.service.impl.RevokeTokenGatewayServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.repository.oauth2.api.BackwardCompatibleTokenRepository;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.service.AuditService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class RevokeTokenGatewayServiceTest {

    @InjectMocks
    private RevokeTokenGatewayService tokenService = new RevokeTokenGatewayServiceImpl();

    @Mock
    private BackwardCompatibleTokenRepository tokenRepository;

    @Mock
    private AuditService auditService;

    @Test
    public void shouldDeleteTokensByUser_withAudit() {
        when(tokenRepository.deleteByUserId("userId")).thenReturn(Completable.complete());
        var user = new User();
        user.setId("userId");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(UUID.randomUUID().toString());
        TestObserver<Void> testObserver = tokenService.deleteByUser(user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldDeleteTokensByUser_withoutAudit() {
        when(tokenRepository.deleteByUserId("userId")).thenReturn(Completable.complete());
        var user = new User();
        user.setId("userId");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(UUID.randomUUID().toString());
        TestObserver<Void> testObserver = tokenService.deleteByUser(user, false).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(auditService, never()).report(any());
    }

    @Test
    public void processMethod_shouldDeleteTokensByDomainAndUser() {
        when(tokenRepository.deleteByDomainIdAndUserId(anyString(), any())).thenReturn(Completable.complete());

        var revoke = RevokeToken.byUser(UUID.randomUUID().toString(), UserId.internal(UUID.randomUUID().toString()));
        TestObserver<Void> testObserver = tokenService.process(new Domain(), revoke).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenRepository).deleteByDomainIdAndUserId(anyString(), any());
        verify(auditService, never()).report(any());
    }

    @Test
    public void processMethod_shouldDeleteTokensByDomainAndClient() {
        when(tokenRepository.deleteByDomainIdAndClientId(anyString(), any())).thenReturn(Completable.complete());

        var revoke = RevokeToken.byClientId(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        TestObserver<Void> testObserver = tokenService.process(new Domain(), revoke).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenRepository).deleteByDomainIdAndClientId(anyString(), any());
        verify(auditService, never()).report(any());
    }

    @Test
    public void processMethod_shouldDeleteTokensByDomainAndClientAndUser() {
        when(tokenRepository.deleteByDomainIdClientIdAndUserId(anyString(), any(), any())).thenReturn(Completable.complete());

        var revoke = RevokeToken.byUserAndClientId(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UserId.internal(UUID.randomUUID().toString()));
        TestObserver<Void> testObserver = tokenService.process(new Domain(), revoke).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenRepository).deleteByDomainIdClientIdAndUserId(anyString(), any(), any());
        verify(auditService, never()).report(any());
    }

}
