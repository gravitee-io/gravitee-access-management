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

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.impl.TokenServiceImpl;
import io.gravitee.am.model.Client;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
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
    private TokenEnhancer tokenEnhancer;

    @Test
    public void shouldCreate_noExistingToken() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        Client client = new Client();
        client.setClientId("my-client-id");

        when(accessTokenRepository.findByCriteria(any())).thenReturn(Maybe.empty());
        when(accessTokenRepository.create(any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));
        when(tokenEnhancer.enhance(any(), any(), any(), any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));

        TestObserver<AccessToken> testObserver = tokenService.create(oAuth2Request, client, null).test();
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

        Client client = new Client();
        client.setClientId("my-client-id");

        io.gravitee.am.repository.oauth2.model.AccessToken existingToken = new io.gravitee.am.repository.oauth2.model.AccessToken();
        existingToken.setExpireAt(new Date(System.currentTimeMillis() + (60 * 1000)));

        when(accessTokenRepository.findByCriteria(any())).thenReturn(Maybe.just(existingToken));

        TestObserver<AccessToken> testObserver = tokenService.create(oAuth2Request, client, null).test();
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

        Client client = new Client();
        client.setClientId("my-client-id");

        io.gravitee.am.repository.oauth2.model.AccessToken existingToken = new io.gravitee.am.repository.oauth2.model.AccessToken();
        existingToken.setExpireAt(new Date(System.currentTimeMillis() - (60 * 1000)));

        when(accessTokenRepository.findByCriteria(any())).thenReturn(Maybe.just(existingToken));
        when(accessTokenRepository.create(any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));
        when(accessTokenRepository.delete(anyString())).thenReturn(Completable.fromSingle(Single.just(new Object())));
        when(tokenEnhancer.enhance(any(), any(), any(), any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));

        TestObserver<AccessToken> testObserver = tokenService.create(oAuth2Request, client, null).test();
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

        Client client = new Client();
        client.setClientId("my-client-id");

        io.gravitee.am.repository.oauth2.model.AccessToken existingToken = new io.gravitee.am.repository.oauth2.model.AccessToken();
        existingToken.setExpireAt(new Date(System.currentTimeMillis() - (60 * 1000)));
        existingToken.setRefreshToken("refresh-token");

        when(accessTokenRepository.findByCriteria(any())).thenReturn(Maybe.just(existingToken));
        when(accessTokenRepository.create(any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));
        when(accessTokenRepository.delete(anyString())).thenReturn(Completable.fromSingle(Single.just(new Object())));
        when(refreshTokenRepository.delete(anyString())).thenReturn(Completable.fromSingle(Single.just(new Object())));
        when(tokenEnhancer.enhance(any(), any(), any(), any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));

        TestObserver<AccessToken> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(accessTokenRepository, times(1)).findByCriteria(any());
        verify(accessTokenRepository, times(1)).create(any());
        verify(accessTokenRepository, times(1)).delete(anyString());
        verify(refreshTokenRepository, times(1)).delete(anyString());
    }

    @Test
    public void shouldCreate_multipleTokensForTheSameAccount() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        Client client = new Client();
        client.setClientId("client-id");
        client.setGenerateNewTokenPerRequest(true);

        when(accessTokenRepository.create(any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));
        when(tokenEnhancer.enhance(any(), any(), any(), any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));

        TestObserver<AccessToken> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(accessTokenRepository, times(1)).create(any());
        verify(accessTokenRepository, never()).findByCriteria(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
    }

    @Test
    public void shouldRefresh_withUser() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId(clientId);

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setClientId(clientId);
        refreshToken.setSubject("subject");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        Client client = new Client();
        client.setClientId(clientId);

        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));
        when(refreshTokenRepository.delete(anyString())).thenReturn(Completable.complete());
        when(accessTokenRepository.findByCriteria(any())).thenReturn(Maybe.empty());
        when(accessTokenRepository.create(any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));
        when(tokenEnhancer.enhance(any(), any(), any(), any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));

        TestObserver<RefreshToken> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, times(1)).delete(anyString());
    }

    @Test
    public void shouldRefresh_withoutUser() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId(clientId);

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setClientId(clientId);
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        Client client = new Client();
        client.setClientId(clientId);

        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));
        when(refreshTokenRepository.delete(anyString())).thenReturn(Completable.complete());
        when(accessTokenRepository.findByCriteria(any())).thenReturn(Maybe.empty());
        when(accessTokenRepository.create(any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));
        when(tokenEnhancer.enhance(any(), any(), any(), any())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));

        TestObserver<RefreshToken> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, times(1)).delete(anyString());
    }

    @Test
    public void shouldNotRefresh_refreshNotFound() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId(clientId);

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setClientId(clientId);
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        Client client = new Client();
        client.setClientId(clientId);

        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.empty());

        TestObserver<RefreshToken> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(accessTokenRepository, never()).findByCriteria(any());
        verify(accessTokenRepository, never()).create(any());
    }

    @Test
    public void shouldNotRefresh_refreshExpired() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId(clientId);

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setClientId(clientId);
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() - 10000));

        Client client = new Client();
        client.setClientId(clientId);

        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));

        TestObserver<RefreshToken> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(accessTokenRepository, never()).findByCriteria(any());
        verify(accessTokenRepository, never()).create(any());
    }

    @Test
    public void shouldNotRefresh_notTheSameClient() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("wrong-client-id");

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setClientId(clientId);
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        Client client = new Client();
        client.setClientId(clientId);
        client.setGenerateNewTokenPerRequest(true);

        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));

        TestObserver<RefreshToken> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(accessTokenRepository, never()).findByCriteria(any());
        verify(accessTokenRepository, never()).create(any());
    }

    @Test
    public void shouldNotRefresh_withUser_userNotFound() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("wrong-client-id");

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setClientId(clientId);
        refreshToken.setSubject("subject");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        Client client = new Client();
        client.setClientId(clientId);

        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));
        when(refreshTokenRepository.delete(anyString())).thenReturn(Completable.complete());

        TestObserver<RefreshToken> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, times(1)).findByToken(any());
    }

}
