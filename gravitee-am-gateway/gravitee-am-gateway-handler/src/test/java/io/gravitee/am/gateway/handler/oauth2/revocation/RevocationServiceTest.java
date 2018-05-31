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
package io.gravitee.am.gateway.handler.oauth2.revocation;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.revocation.impl.RevocationTokenServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.token.impl.DefaultAccessToken;
import io.gravitee.am.gateway.handler.oauth2.utils.TokenTypeHint;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

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
        revocationTokenRequest.setClientId("wrong-client-id");

        DefaultAccessToken accessToken = new DefaultAccessToken("token");
        accessToken.setClientId("client-id");

        when(tokenService.getAccessToken("token")).thenReturn(Maybe.just(accessToken));
        when(tokenService.getRefreshToken("token")).thenReturn(Maybe.empty());

        TestObserver testObserver = revocationTokenService.revoke(revocationTokenRequest).test();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(tokenService, times(1)).getAccessToken("token");
        verify(tokenService, never()).deleteAccessToken(anyString());
        verify(tokenService, never()).getRefreshToken("token");
        verify(tokenService, never()).deleteRefreshToken(anyString());
    }

    @Test
    public void shouldRevoke_evenWithInvalidToken() {
        final RevocationTokenRequest revocationTokenRequest = new RevocationTokenRequest("token");

        when(tokenService.getAccessToken("token")).thenReturn(Maybe.empty());
        when(tokenService.getRefreshToken("token")).thenReturn(Maybe.empty());

        TestObserver testObserver = revocationTokenService.revoke(revocationTokenRequest).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenService, times(1)).getAccessToken("token");
        verify(tokenService, never()).deleteAccessToken(anyString());
        verify(tokenService, times(1)).getRefreshToken("token");
        verify(tokenService, never()).deleteRefreshToken(anyString());

    }

    @Test
    public void shouldRevoke_accessToken() {
        final RevocationTokenRequest revocationTokenRequest = new RevocationTokenRequest("token");
        revocationTokenRequest.setClientId("client-id");

        DefaultAccessToken accessToken = new DefaultAccessToken("token");
        accessToken.setClientId("client-id");

        when(tokenService.getAccessToken("token")).thenReturn(Maybe.just(accessToken));
        when(tokenService.deleteAccessToken("token")).thenReturn(Completable.complete());

        TestObserver testObserver = revocationTokenService.revoke(revocationTokenRequest).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenService, times(1)).getAccessToken("token");
        verify(tokenService, times(1)).deleteAccessToken("token");
        verify(tokenService, never()).getRefreshToken(anyString());
        verify(tokenService, never()).deleteRefreshToken(anyString());

    }

    @Test
    public void shouldRevoke_refreshToken() {
        final RevocationTokenRequest revocationTokenRequest = new RevocationTokenRequest("token");
        revocationTokenRequest.setClientId("client-id");
        revocationTokenRequest.setHint(TokenTypeHint.REFRESH_TOKEN);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("token");
        refreshToken.setClientId("client-id");

        when(tokenService.getRefreshToken("token")).thenReturn(Maybe.just(refreshToken));
        when(tokenService.deleteRefreshToken("token")).thenReturn(Completable.complete());

        TestObserver testObserver = revocationTokenService.revoke(revocationTokenRequest).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenService, times(1)).getRefreshToken("token");
        verify(tokenService, times(1)).deleteRefreshToken("token");
        verify(tokenService, never()).getAccessToken("token");
        verify(tokenService, never()).deleteAccessToken("token");

    }
}
