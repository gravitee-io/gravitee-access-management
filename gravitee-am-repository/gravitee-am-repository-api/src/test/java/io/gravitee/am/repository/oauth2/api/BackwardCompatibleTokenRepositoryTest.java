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
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
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

    // --- findRefreshTokenByJti ---

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
    public void shouldFallbackToLegacyRefreshTokenRepositoryWhenEnabled() {
        RefreshToken legacyToken = new RefreshToken();
        when(tokenRepository.findRefreshTokenByJti("refresh-jti")).thenReturn(Maybe.empty());
        when(refreshTokenRepository.findByToken("refresh-jti")).thenReturn(Maybe.just(legacyToken));

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                true);

        TestObserver<RefreshToken> observer = repository.findRefreshTokenByJti("refresh-jti").test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(legacyToken);

        verify(refreshTokenRepository).findByToken("refresh-jti");
    }

    @Test
    public void shouldReturnPrimaryRefreshTokenWithoutSubscribingToLegacyWhenFoundInPrimary() {
        RefreshToken primaryToken = new RefreshToken();
        RefreshToken legacyToken = new RefreshToken();
        when(tokenRepository.findRefreshTokenByJti("refresh-jti")).thenReturn(Maybe.just(primaryToken));
        when(refreshTokenRepository.findByToken("refresh-jti")).thenReturn(Maybe.just(legacyToken));

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                true);

        TestObserver<RefreshToken> observer = repository.findRefreshTokenByJti("refresh-jti").test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValueCount(1);
        observer.assertValue(primaryToken);
    }

    // --- findAccessTokenByJti ---

    @Test
    public void shouldNotFallbackToLegacyAccessTokenRepositoryWhenDisabled() {
        when(tokenRepository.findAccessTokenByJti("access-jti")).thenReturn(Maybe.empty());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                false);

        TestObserver<AccessToken> observer = repository.findAccessTokenByJti("access-jti").test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertNoValues();

        verify(accessTokenRepository, never()).findByToken(anyString());
    }

    @Test
    public void shouldFallbackToLegacyAccessTokenRepositoryWhenEnabled() {
        AccessToken legacyToken = new AccessToken();
        when(tokenRepository.findAccessTokenByJti("access-jti")).thenReturn(Maybe.empty());
        when(accessTokenRepository.findByToken("access-jti")).thenReturn(Maybe.just(legacyToken));

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                true);

        TestObserver<AccessToken> observer = repository.findAccessTokenByJti("access-jti").test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(legacyToken);

        verify(accessTokenRepository).findByToken("access-jti");
    }

    @Test
    public void shouldReturnPrimaryAccessTokenWithoutSubscribingToLegacyWhenFoundInPrimary() {
        AccessToken primaryToken = new AccessToken();
        AccessToken legacyToken = new AccessToken();
        when(tokenRepository.findAccessTokenByJti("access-jti")).thenReturn(Maybe.just(primaryToken));
        when(accessTokenRepository.findByToken("access-jti")).thenReturn(Maybe.just(legacyToken));

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                true);

        TestObserver<AccessToken> observer = repository.findAccessTokenByJti("access-jti").test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValueCount(1);
        observer.assertValue(primaryToken);
    }

    // --- findAccessTokenByAuthorizationCode ---

    @Test
    public void shouldNotFallbackToLegacyAccessTokenByAuthCodeRepositoryWhenDisabled() {
        when(tokenRepository.findAccessTokenByAuthorizationCode("auth-code")).thenReturn(Observable.empty());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                false);

        TestObserver<AccessToken> observer = repository.findAccessTokenByAuthorizationCode("auth-code").test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertNoValues();

        verify(accessTokenRepository, never()).findByAuthorizationCode(anyString());
    }

    @Test
    public void shouldFallbackToLegacyAccessTokenByAuthCodeRepositoryWhenEnabled() {
        AccessToken legacyToken = new AccessToken();
        when(tokenRepository.findAccessTokenByAuthorizationCode("auth-code")).thenReturn(Observable.empty());
        when(accessTokenRepository.findByAuthorizationCode("auth-code")).thenReturn(Observable.just(legacyToken));

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                true);

        TestObserver<AccessToken> observer = repository.findAccessTokenByAuthorizationCode("auth-code").test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(legacyToken);

        verify(accessTokenRepository).findByAuthorizationCode("auth-code");
    }

    @Test
    public void shouldReturnPrimaryAccessTokenByAuthCodeWithoutSubscribingToLegacyWhenFoundInPrimary() {
        AccessToken primaryToken = new AccessToken();
        AccessToken legacyToken = new AccessToken();
        when(tokenRepository.findAccessTokenByAuthorizationCode("auth-code")).thenReturn(Observable.just(primaryToken));
        when(accessTokenRepository.findByAuthorizationCode("auth-code")).thenReturn(Observable.just(legacyToken));

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                true);

        TestObserver<AccessToken> observer = repository.findAccessTokenByAuthorizationCode("auth-code").test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValueCount(1);
        observer.assertValue(primaryToken);
    }

    // --- deleteByJti ---

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

    @Test
    public void shouldDeleteByJtiDelegatesToLegacyRepositoriesWhenEnabled() {
        when(tokenRepository.deleteByJti("jti")).thenReturn(Completable.complete());
        when(accessTokenRepository.delete("jti")).thenReturn(Completable.complete());
        when(refreshTokenRepository.delete("jti")).thenReturn(Completable.complete());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                true);

        TestObserver<Void> observer = repository.deleteByJti("jti").test();
        observer.assertComplete();
        observer.assertNoErrors();

        verify(tokenRepository).deleteByJti("jti");
        verify(accessTokenRepository).delete("jti");
        verify(refreshTokenRepository).delete("jti");
    }

    // --- deleteByUserId ---

    @Test
    public void shouldNotDeleteByUserIdFromLegacyRepositoriesWhenDisabled() {
        when(tokenRepository.deleteByUserId("user-id")).thenReturn(Completable.complete());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                false);

        TestObserver<Void> observer = repository.deleteByUserId("user-id").test();
        observer.assertComplete();
        observer.assertNoErrors();

        verify(tokenRepository).deleteByUserId("user-id");
        verify(accessTokenRepository, never()).deleteByUserId(anyString());
        verify(refreshTokenRepository, never()).deleteByUserId(anyString());
    }

    @Test
    public void shouldDeleteByUserIdDelegatesToLegacyRepositoriesWhenEnabled() {
        when(tokenRepository.deleteByUserId("user-id")).thenReturn(Completable.complete());
        when(accessTokenRepository.deleteByUserId("user-id")).thenReturn(Completable.complete());
        when(refreshTokenRepository.deleteByUserId("user-id")).thenReturn(Completable.complete());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                true);

        TestObserver<Void> observer = repository.deleteByUserId("user-id").test();
        observer.assertComplete();
        observer.assertNoErrors();

        verify(tokenRepository).deleteByUserId("user-id");
        verify(accessTokenRepository).deleteByUserId("user-id");
        verify(refreshTokenRepository).deleteByUserId("user-id");
    }

    // --- deleteByDomainIdClientIdAndUserId ---

    @Test
    public void shouldNotDeleteByDomainIdClientIdAndUserIdFromLegacyRepositoriesWhenDisabled() {
        UserId userId = UserId.internal("user-id");
        when(tokenRepository.deleteByDomainIdClientIdAndUserId("domain", "client", userId)).thenReturn(Completable.complete());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                false);

        TestObserver<Void> observer = repository.deleteByDomainIdClientIdAndUserId("domain", "client", userId).test();
        observer.assertComplete();
        observer.assertNoErrors();

        verify(tokenRepository).deleteByDomainIdClientIdAndUserId("domain", "client", userId);
        verify(accessTokenRepository, never()).deleteByDomainIdClientIdAndUserId(anyString(), anyString(), any(UserId.class));
        verify(refreshTokenRepository, never()).deleteByDomainIdClientIdAndUserId(anyString(), anyString(), any(UserId.class));
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

    // --- deleteByDomainIdAndUserId ---

    @Test
    public void shouldNotDeleteByDomainIdAndUserIdFromLegacyRepositoriesWhenDisabled() {
        UserId userId = UserId.internal("user-id");
        when(tokenRepository.deleteByDomainIdAndUserId("domain", userId)).thenReturn(Completable.complete());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                false);

        TestObserver<Void> observer = repository.deleteByDomainIdAndUserId("domain", userId).test();
        observer.assertComplete();
        observer.assertNoErrors();

        verify(tokenRepository).deleteByDomainIdAndUserId("domain", userId);
        verify(accessTokenRepository, never()).deleteByDomainIdAndUserId(anyString(), any(UserId.class));
        verify(refreshTokenRepository, never()).deleteByDomainIdAndUserId(anyString(), any(UserId.class));
    }

    @Test
    public void shouldDeleteByDomainIdAndUserIdDelegatesToLegacyRepositoriesWhenEnabled() {
        UserId userId = UserId.internal("user-id");
        when(tokenRepository.deleteByDomainIdAndUserId("domain", userId)).thenReturn(Completable.complete());
        when(accessTokenRepository.deleteByDomainIdAndUserId("domain", userId)).thenReturn(Completable.complete());
        when(refreshTokenRepository.deleteByDomainIdAndUserId("domain", userId)).thenReturn(Completable.complete());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                true);

        TestObserver<Void> observer = repository.deleteByDomainIdAndUserId("domain", userId).test();
        observer.assertComplete();
        observer.assertNoErrors();

        verify(tokenRepository).deleteByDomainIdAndUserId("domain", userId);
        verify(accessTokenRepository).deleteByDomainIdAndUserId("domain", userId);
        verify(refreshTokenRepository).deleteByDomainIdAndUserId("domain", userId);
    }

    // --- deleteByDomainIdAndClientId ---

    @Test
    public void shouldNotDeleteByDomainIdAndClientIdFromLegacyRepositoriesWhenDisabled() {
        when(tokenRepository.deleteByDomainIdAndClientId("domain", "client")).thenReturn(Completable.complete());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                false);

        TestObserver<Void> observer = repository.deleteByDomainIdAndClientId("domain", "client").test();
        observer.assertComplete();
        observer.assertNoErrors();

        verify(tokenRepository).deleteByDomainIdAndClientId("domain", "client");
        verify(accessTokenRepository, never()).deleteByDomainIdAndClientId(anyString(), anyString());
        verify(refreshTokenRepository, never()).deleteByDomainIdAndClientId(anyString(), anyString());
    }

    @Test
    public void shouldDeleteByDomainIdAndClientIdDelegatesToLegacyRepositoriesWhenEnabled() {
        when(tokenRepository.deleteByDomainIdAndClientId("domain", "client")).thenReturn(Completable.complete());
        when(accessTokenRepository.deleteByDomainIdAndClientId("domain", "client")).thenReturn(Completable.complete());
        when(refreshTokenRepository.deleteByDomainIdAndClientId("domain", "client")).thenReturn(Completable.complete());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                true);

        TestObserver<Void> observer = repository.deleteByDomainIdAndClientId("domain", "client").test();
        observer.assertComplete();
        observer.assertNoErrors();

        verify(tokenRepository).deleteByDomainIdAndClientId("domain", "client");
        verify(accessTokenRepository).deleteByDomainIdAndClientId("domain", "client");
        verify(refreshTokenRepository).deleteByDomainIdAndClientId("domain", "client");
    }

    // --- create(RefreshToken) ---

    @Test
    public void shouldDelegateCreateRefreshTokenToTokenRepository() {
        RefreshToken refreshToken = new RefreshToken();
        when(tokenRepository.create(refreshToken)).thenReturn(Single.just(refreshToken));

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                false);

        TestObserver<RefreshToken> observer = repository.create(refreshToken).test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(refreshToken);

        verify(tokenRepository).create(refreshToken);
    }

    // --- create(AccessToken) ---

    @Test
    public void shouldDelegateCreateAccessTokenToTokenRepository() {
        AccessToken accessToken = new AccessToken();
        when(tokenRepository.create(accessToken)).thenReturn(Single.just(accessToken));

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                false);

        TestObserver<AccessToken> observer = repository.create(accessToken).test();
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(accessToken);

        verify(tokenRepository).create(accessToken);
    }

    // --- purgeExpiredData ---

    @Test
    public void shouldDelegatePurgeExpiredDataToTokenRepository() {
        when(tokenRepository.purgeExpiredData()).thenReturn(Completable.complete());

        BackwardCompatibleTokenRepository repository = new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                false);

        TestObserver<Void> observer = repository.purgeExpiredData().test();
        observer.assertComplete();
        observer.assertNoErrors();

        verify(tokenRepository).purgeExpiredData();
    }
}
