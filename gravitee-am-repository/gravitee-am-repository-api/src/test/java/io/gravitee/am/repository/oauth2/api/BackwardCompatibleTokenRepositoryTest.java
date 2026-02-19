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
package io.gravitee.am.repository.oauth2.api;

import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BackwardCompatibleTokenRepositoryTest {

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;


    @Test
    public void shouldNotFallbackToLegacyRefreshTokenRepositoryWhenDisabled() {
        when(tokenRepository.findRefreshTokenByJti("refresh-jti")).thenReturn(Maybe.empty());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                false);

        TestObserver<RefreshToken> observer = repository.findRefreshTokenByJti("refresh-jti").test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertNoValues();

        verify(refreshTokenRepository, never()).findByToken(anyString());
    }







    @Test
    public void shouldDeleteByDomainIdClientIdAndUserIdDelegatesToLegacyRepositoriesWhenEnabled() {
        UserId userId = UserId.internal("user-id");
        when(tokenRepository.deleteByDomainIdClientIdAndUserId("domain", "client", userId)).thenReturn(Completable.complete());
        when(accessTokenRepository.deleteByDomainIdClientIdAndUserId("domain", "client", userId)).thenReturn(Completable.complete());
        when(refreshTokenRepository.deleteByDomainIdClientIdAndUserId("domain", "client", userId)).thenReturn(Completable.complete());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                true);

        TestObserver<Void> observer = repository.deleteByDomainIdClientIdAndUserId("domain", "client", userId).test();
        observer.assertComplete();
        observer.assertNoErrors();

        verify(tokenRepository).deleteByDomainIdClientIdAndUserId("domain", "client", userId);
        verify(accessTokenRepository).deleteByDomainIdClientIdAndUserId("domain", "client", userId);
        verify(refreshTokenRepository).deleteByDomainIdClientIdAndUserId("domain", "client", userId);
    }

    @Test
    public void shouldNotDeleteLegacyRepositoriesWhenDisabled() {
        when(tokenRepository.deleteByJti("jti")).thenReturn(Completable.complete());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                false);

        TestObserver<Void> observer = repository.deleteByJti("jti").test();
        observer.assertComplete();
        observer.assertNoErrors();

        verify(tokenRepository).deleteByJti("jti");
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
    }
}
