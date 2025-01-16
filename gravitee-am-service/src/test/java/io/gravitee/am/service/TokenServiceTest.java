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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.service.impl.TokenServiceImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class TokenServiceTest {

    @InjectMocks
    private TokenService tokenService = new TokenServiceImpl();

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditService auditService;

    @Test
    public void shouldDeleteTokensByUser() {
        when(accessTokenRepository.deleteByUserId("userId")).thenReturn(Completable.complete());
        when(refreshTokenRepository.deleteByUserId("userId")).thenReturn(Completable.complete());
        var user = new User();
        user.setId("userId");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(UUID.randomUUID().toString());
        TestObserver<Void> testObserver = tokenService.deleteByUser(user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(auditService, times(1)).report(any());
    }

}
