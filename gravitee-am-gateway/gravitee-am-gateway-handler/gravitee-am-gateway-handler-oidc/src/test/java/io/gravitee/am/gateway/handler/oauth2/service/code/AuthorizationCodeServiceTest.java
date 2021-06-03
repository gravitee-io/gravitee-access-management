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
package io.gravitee.am.gateway.handler.oauth2.service.code;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.code.impl.AuthorizationCodeServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationCodeServiceTest {

    @InjectMocks
    private AuthorizationCodeService authorizationCodeService = new AuthorizationCodeServiceImpl();

    @Mock
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    public void shouldCreate_noExistingCode() {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId("my-client-id");

        User user = new User();
        user.setUsername("my-username-id");

        when(authorizationCodeRepository.create(any())).thenReturn(Single.just(new AuthorizationCode()));

        TestObserver<AuthorizationCode> testObserver = authorizationCodeService.create(authorizationRequest, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(authorizationCodeRepository, times(1)).create(any());
    }


    @Test
    public void shouldRemove_existingCode() {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId("my-client-id");

        Client client = new Client();
        client.setClientId("my-client-id");

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setId("code-id");
        authorizationCode.setCode("my-code");
        authorizationCode.setClientId("my-client-id");

        when(authorizationCodeRepository.findByCode(authorizationCode.getCode())).thenReturn(Maybe.just(authorizationCode));
        when(authorizationCodeRepository.delete(authorizationCode.getId())).thenReturn(Maybe.just(authorizationCode));
        when(accessTokenRepository.findByAuthorizationCode(authorizationCode.getCode())).thenReturn(Observable.empty());

        TestObserver<AuthorizationCode> testObserver = authorizationCodeService.remove(authorizationCode.getCode(), client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(authorizationCodeRepository, times(1)).findByCode(any());
        verify(authorizationCodeRepository, times(1)).delete(any());
        verify(accessTokenRepository, times(1)).findByAuthorizationCode(anyString());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
    }

    @Test
    public void shouldRemove_invalidCode_existingTokens_noRefreshToken() {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId("my-client-id");

        Client client = new Client();
        client.setClientId("my-client-id");

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("my-code");
        authorizationCode.setClientId("my-client-id");

        AccessToken accessToken = new AccessToken();
        accessToken.setToken("my-access-token-1");
        accessToken.setAuthorizationCode("my-code");

        AccessToken accessToken2 = new AccessToken();
        accessToken2.setToken("my-access-token-2");
        accessToken2.setAuthorizationCode("my-code");

        List<AccessToken> tokens = Arrays.asList(accessToken, accessToken2);

        when(authorizationCodeRepository.findByCode(any())).thenReturn(Maybe.empty());
        when(accessTokenRepository.findByAuthorizationCode(anyString())).thenReturn(Observable.fromIterable(tokens));
        when(accessTokenRepository.delete(anyString())).thenReturn(Completable.complete());

        TestObserver<AuthorizationCode> testObserver = authorizationCodeService.remove(authorizationCode.getCode(), client).test();
        testObserver.assertError(InvalidGrantException.class);

        verify(authorizationCodeRepository, times(1)).findByCode(any());
        verify(accessTokenRepository, times(1)).findByAuthorizationCode(anyString());
        verify(accessTokenRepository, times(2)).delete(anyString());
        verify(authorizationCodeRepository, never()).delete(any());
        verify(refreshTokenRepository, never()).delete(anyString());
    }

    @Test
    public void shouldRemove_invalidCode_existingTokens_refreshTokens() {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId("my-client-id");

        Client client = new Client();
        client.setClientId("my-client-id");

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode("my-code");
        authorizationCode.setClientId("my-client-id");

        AccessToken accessToken = new AccessToken();
        accessToken.setToken("my-access-token-1");
        accessToken.setAuthorizationCode("my-code");
        accessToken.setRefreshToken("my-refresh-token-1");

        AccessToken accessToken2 = new AccessToken();
        accessToken2.setToken("my-access-token-2");
        accessToken2.setAuthorizationCode("my-code");
        accessToken2.setRefreshToken("my-refresh-token-2");

        List<AccessToken> tokens = Arrays.asList(accessToken, accessToken2);

        when(authorizationCodeRepository.findByCode(any())).thenReturn(Maybe.empty());
        when(accessTokenRepository.findByAuthorizationCode(anyString())).thenReturn(Observable.fromIterable(tokens));
        when(accessTokenRepository.delete(anyString())).thenReturn(Completable.complete());
        when(refreshTokenRepository.delete(anyString())).thenReturn(Completable.complete());

        TestObserver<AuthorizationCode> testObserver = authorizationCodeService.remove(authorizationCode.getCode(), client).test();
        testObserver.assertError(InvalidGrantException.class);

        verify(authorizationCodeRepository, times(1)).findByCode(any());
        verify(accessTokenRepository, times(1)).findByAuthorizationCode(anyString());
        verify(accessTokenRepository, times(2)).delete(anyString());
        verify(refreshTokenRepository, times(2)).delete(anyString());
        verify(authorizationCodeRepository, never()).delete(any());
    }
}
