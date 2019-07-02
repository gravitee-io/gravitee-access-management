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
package io.gravitee.am.gateway.handler.oauth2.service.token;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.TokenServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.model.Client;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private JWTService jwtService;

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Test
    public void shouldCreate() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        Client client = new Client();
        client.setClientId("my-client-id");

        ArgumentCaptor<io.gravitee.am.repository.oauth2.model.AccessToken> accessTokenCaptor = ArgumentCaptor.forClass(io.gravitee.am.repository.oauth2.model.AccessToken.class);

        when(jwtService.encode(any(), any(Client.class))).thenReturn(Single.just(""));
        when(accessTokenRepository.create(accessTokenCaptor.capture())).thenReturn(Single.just(new io.gravitee.am.repository.oauth2.model.AccessToken()));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("token-id")));

        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(accessTokenRepository, times(1)).create(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());

        Assert.assertTrue("client should be client_id", client.getClientId().equals(accessTokenCaptor.getValue().getClient()));
    }

    @Test
    public void shouldRefresh() {
        String clientId = "client-id";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId(clientId);

        Client client = new Client();
        client.setId(clientId);
        client.setClientId(clientId);

        String token = "refresh-token";
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(token);
        refreshToken.setToken(token);
        refreshToken.setSubject("subject");
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        JWT jwt = new JWT();
        jwt.setJti(token);
        jwt.setAud(clientId);
        jwt.setExp(refreshToken.getExpireAt().getTime() / 1000l);

        when(jwtService.decodeAndVerify(any(), any())).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));
        when(refreshTokenRepository.delete(anyString())).thenReturn(Completable.complete());

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest, client).test();
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
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        Client client = new Client();
        client.setClientId(clientId);

        JWT jwt = new JWT();
        jwt.setJti(token);
        jwt.setAud(clientId);
        jwt.setExp(refreshToken.getExpireAt().getTime() / 1000l);

        when(jwtService.decodeAndVerify(any(), any())).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.empty());

        TestObserver<Token> testObserver = tokenService.refresh(any(), tokenRequest, any()).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());
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
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() - 10000));

        Client client = new Client();
        client.setClientId(clientId);

        JWT jwt = new JWT();
        jwt.setJti(token);
        jwt.setAud(clientId);
        jwt.setExp(refreshToken.getExpireAt().getTime() / 1000l);

        when(jwtService.decodeAndVerify(any(), any())).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), any(), any()).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());
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
        refreshToken.setExpireAt(new Date(System.currentTimeMillis() + 10000));

        Client client = new Client();
        client.setClientId(clientId);

        JWT jwt = new JWT();
        jwt.setJti(token);
        jwt.setAud(clientId);
        jwt.setExp(refreshToken.getExpireAt().getTime() / 1000l);

        when(jwtService.decodeAndVerify(any(), any())).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(accessTokenRepository, never()).create(any());
    }
}
