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
package io.gravitee.am.gateway.handler.oauth2.service.revocation;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.revocation.impl.RevocationTokenServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.RefreshToken;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RevocationServiceTest {

    @InjectMocks
    private RevocationTokenService revocationTokenService = new RevocationTokenServiceImpl();

    @Mock
    private TokenService tokenService;

    @Test
    public void shouldNotRevoke_WrongRequestedClientId() {
        final RevocationTokenRequest revocationTokenRequest = new RevocationTokenRequest("token");

        AccessToken accessToken = new AccessToken("token");
        accessToken.setClientId("client-id");

        Client client = new Client();
        client.setClientId("wrong-client-id");

        when(tokenService.getAccessToken("token", client)).thenReturn(Maybe.just(accessToken));

        TestObserver testObserver = revocationTokenService.revoke(revocationTokenRequest, client).test();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(tokenService, times(1)).getAccessToken("token", client);
        verify(tokenService, never()).deleteAccessToken(anyString());
        verify(tokenService, never()).getRefreshToken("token", client);
        verify(tokenService, never()).deleteRefreshToken(anyString());
    }

    @Test
    public void shouldRevoke_evenWithInvalidToken() {
        final RevocationTokenRequest revocationTokenRequest = new RevocationTokenRequest("token");

        Client client = new Client();
        client.setClientId("client-id");

        when(tokenService.getAccessToken("token", client)).thenReturn(Maybe.empty());
        when(tokenService.getRefreshToken("token", client)).thenReturn(Maybe.empty());

        TestObserver testObserver = revocationTokenService.revoke(revocationTokenRequest, client).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenService, times(1)).getAccessToken("token", client);
        verify(tokenService, never()).deleteAccessToken(anyString());
        verify(tokenService, times(1)).getRefreshToken("token", client);
        verify(tokenService, never()).deleteRefreshToken(anyString());

    }

    @Test
    public void shouldRevoke_accessToken() {
        final RevocationTokenRequest revocationTokenRequest = new RevocationTokenRequest("token");

        Client client = new Client();
        client.setClientId("client-id");

        AccessToken accessToken = new AccessToken("token");
        accessToken.setClientId("client-id");

        when(tokenService.getAccessToken("token", client)).thenReturn(Maybe.just(accessToken));
        when(tokenService.deleteAccessToken("token")).thenReturn(Completable.complete());

        TestObserver testObserver = revocationTokenService.revoke(revocationTokenRequest, client).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenService, times(1)).getAccessToken("token", client);
        verify(tokenService, times(1)).deleteAccessToken("token");
        verify(tokenService, never()).getRefreshToken(anyString(), any());
        verify(tokenService, never()).deleteRefreshToken(anyString());

    }

    @Test
    public void shouldRevoke_refreshToken() {
        final RevocationTokenRequest revocationTokenRequest = new RevocationTokenRequest("token");
        revocationTokenRequest.setHint(TokenTypeHint.REFRESH_TOKEN);

        Client client = new Client();
        client.setClientId("client-id");

        Token refreshToken = new RefreshToken("token");
        refreshToken.setClientId("client-id");

        when(tokenService.getRefreshToken("token", client)).thenReturn(Maybe.just(refreshToken));
        when(tokenService.deleteRefreshToken("token")).thenReturn(Completable.complete());

        TestObserver testObserver = revocationTokenService.revoke(revocationTokenRequest, client).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenService, times(1)).getRefreshToken("token", client);
        verify(tokenService, times(1)).deleteRefreshToken("token");
        verify(tokenService, never()).getAccessToken("token", client);
        verify(tokenService, never()).deleteAccessToken("token");

    }
}
