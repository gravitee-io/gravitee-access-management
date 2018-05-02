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
package io.gravitee.am.gateway.handler.oauth2.token;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.token.impl.TokenServiceImpl;
import io.gravitee.am.model.Client;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

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
    private ClientService clientService;

    @Test
    public void shouldCreate_noExistingToken() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(new Client()));
        when(accessTokenRepository.findByCriteria(any())).thenReturn(Maybe.empty());
        when(accessTokenRepository.create(any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));

        TestObserver<AccessToken> testObserver = tokenService.create(oAuth2Request).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(accessTokenRepository, times(1)).findByCriteria(any());
        verify(accessTokenRepository, times(1)).create(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
    }

    @Test
    public void shouldCreate_existingNoExpiredToken() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        io.gravitee.am.repository.oauth2.model.AccessToken existingToken = new io.gravitee.am.repository.oauth2.model.AccessToken();
        existingToken.setExpireAt(new Date(System.currentTimeMillis() + (60 * 1000)));

        when(accessTokenRepository.findByCriteria(any())).thenReturn(Maybe.just(existingToken));

        TestObserver<AccessToken> testObserver = tokenService.create(oAuth2Request).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(accessTokenRepository, times(1)).findByCriteria(any());
        verify(accessTokenRepository, never()).create(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
    }

    @Test
    public void shouldCreate_existingExpiredToken_noRefreshToken() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        io.gravitee.am.repository.oauth2.model.AccessToken existingToken = new io.gravitee.am.repository.oauth2.model.AccessToken();
        existingToken.setExpireAt(new Date(System.currentTimeMillis() - (60 * 1000)));

        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(new Client()));
        when(accessTokenRepository.findByCriteria(any())).thenReturn(Maybe.just(existingToken));
        when(accessTokenRepository.create(any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));
        when(accessTokenRepository.delete(anyString())).thenReturn(Completable.fromSingle(Single.just(new Object())));

        TestObserver<AccessToken> testObserver = tokenService.create(oAuth2Request).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(accessTokenRepository, times(1)).findByCriteria(any());
        verify(accessTokenRepository, times(1)).create(any());
        verify(accessTokenRepository, times(1)).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
    }

    @Test
    public void shouldCreate_existingExpiredToken_withRefreshToken() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        io.gravitee.am.repository.oauth2.model.AccessToken existingToken = new io.gravitee.am.repository.oauth2.model.AccessToken();
        existingToken.setExpireAt(new Date(System.currentTimeMillis() - (60 * 1000)));
        existingToken.setRefreshToken("refresh-token");

        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(new Client()));
        when(accessTokenRepository.findByCriteria(any())).thenReturn(Maybe.just(existingToken));
        when(accessTokenRepository.create(any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));
        when(accessTokenRepository.delete(anyString())).thenReturn(Completable.fromSingle(Single.just(new Object())));
        when(refreshTokenRepository.delete(anyString())).thenReturn(Completable.fromSingle(Single.just(new Object())));

        TestObserver<AccessToken> testObserver = tokenService.create(oAuth2Request).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(accessTokenRepository, times(1)).findByCriteria(any());
        verify(accessTokenRepository, times(1)).create(any());
        verify(accessTokenRepository, times(1)).delete(anyString());
        verify(refreshTokenRepository, times(1)).delete(anyString());
    }


}
