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
package io.gravitee.am.gateway.handler.common.oauth2.impl;

import io.gravitee.am.common.exception.jwt.JWTException;
import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.ACCESS_TOKEN;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.REFRESH_TOKEN;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class IntrospectionRefreshTokenServiceTest {

    @Mock
    private JWTService jwtService;

    @Mock
    private ClientSyncService clientService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private IntrospectionRefreshTokenService introspectionTokenService;

    @Before
    public void setUp() throws Exception {
        introspectionTokenService = new IntrospectionRefreshTokenService(jwtService, clientService, refreshTokenRepository);
    }

    @Test
    public void shouldIntrospect_validToken_offline_verification() {
        final String token = "token";
        final JWT jwt = new JWT();
        jwt.setJti("jti");
        jwt.setDomain("domain");
        jwt.setAud("client");
        final Client client = new Client();
        client.setClientId("client-id");

        when(jwtService.decode(token, REFRESH_TOKEN)).thenReturn(Single.just(jwt));
        when(clientService.findByDomainAndClientId(jwt.getDomain(), jwt.getAud())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(token, client, REFRESH_TOKEN)).thenReturn(Single.just(jwt));

        TestObserver testObserver = introspectionTokenService.introspect(token, true).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(refreshTokenRepository, never()).findByToken(jwt.getJti());
    }

    @Test
    public void shouldIntrospect_validToken_online_verification() {
        final String token = "token";

        final JWT jwt = new JWT();
        jwt.setJti("jti");
        jwt.setDomain("domain");
        jwt.setAud("client");
        jwt.setIat(Instant.now().minus(1, ChronoUnit.DAYS).getEpochSecond());

        final Client client = new Client();
        client.setClientId("client-id");

        final RefreshToken accessToken = new RefreshToken();
        accessToken.setExpireAt(new Date(Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli()));

        when(jwtService.decode(token, REFRESH_TOKEN)).thenReturn(Single.just(jwt));
        when(clientService.findByDomainAndClientId(jwt.getDomain(), jwt.getAud())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(token, client, REFRESH_TOKEN)).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(jwt.getJti())).thenReturn(Maybe.just(accessToken));

        TestObserver testObserver = introspectionTokenService.introspect(token, false).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(refreshTokenRepository, times(1)).findByToken(jwt.getJti());
    }

    @Test
    public void shouldIntrospect_validToken_offline_verification_timer() {
        final String token = "token";
        final JWT jwt = new JWT();
        jwt.setJti("jti");
        jwt.setDomain("domain");
        jwt.setAud("client");
        jwt.setIat(Instant.now().getEpochSecond());
        final Client client = new Client();
        client.setClientId("client-id");

        when(jwtService.decode(token, REFRESH_TOKEN)).thenReturn(Single.just(jwt));
        when(clientService.findByDomainAndClientId(jwt.getDomain(), jwt.getAud())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(token, client, REFRESH_TOKEN)).thenReturn(Single.just(jwt));

        TestObserver testObserver = introspectionTokenService.introspect(token, false).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        // repository should not be call because the token is too recent
        verify(refreshTokenRepository, never()).findByToken(jwt.getJti());
    }

    @Test
    public void shouldIntrospect_invalidValidToken_jwt_exception() {
        final String token = "token";
        final JWT jwt = new JWT();
        jwt.setJti("jti");
        jwt.setDomain("domain");
        jwt.setAud("client");
        jwt.setIat(Instant.now().getEpochSecond());
        final Client client = new Client();
        client.setClientId("client-id");

        when(jwtService.decode(token, REFRESH_TOKEN)).thenReturn(Single.just(jwt));
        when(clientService.findByDomainAndClientId(jwt.getDomain(), jwt.getAud())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(token, client, REFRESH_TOKEN)).thenReturn(Single.error(new JWTException("invalid token")));

        TestObserver testObserver = introspectionTokenService.introspect(token, false).test();
        testObserver.assertError(InvalidTokenException.class);
        verify(refreshTokenRepository, never()).findByToken(jwt.getJti());
    }

    @Test
    public void shouldIntrospect_invalidValidToken_token_revoked() {
        final String token = "token";
        final JWT jwt = new JWT();
        jwt.setJti("jti");
        jwt.setDomain("domain");
        jwt.setAud("client");
        jwt.setIat(Instant.now().minus(1, ChronoUnit.DAYS).getEpochSecond());
        final Client client = new Client();
        client.setClientId("client-id");

        when(jwtService.decode(token, REFRESH_TOKEN)).thenReturn(Single.just(jwt));
        when(clientService.findByDomainAndClientId(jwt.getDomain(), jwt.getAud())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(token, client, REFRESH_TOKEN)).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(jwt.getJti())).thenReturn(Maybe.empty());

        TestObserver testObserver = introspectionTokenService.introspect(token, false).test();
        testObserver.assertError(InvalidTokenException.class);
        verify(refreshTokenRepository, times(1)).findByToken(jwt.getJti());
    }

    @Test
    public void shouldIntrospect_invalidValidToken_token_expired() {
        final String token = "token";
        final JWT jwt = new JWT();
        jwt.setJti("jti");
        jwt.setDomain("domain");
        jwt.setAud("client");
        jwt.setIat(Instant.now().minus(1, ChronoUnit.DAYS).getEpochSecond());
        final Client client = new Client();
        client.setClientId("client-id");

        final RefreshToken refreshToken = new RefreshToken();
        refreshToken.setExpireAt(new Date(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()));

        when(jwtService.decode(token, REFRESH_TOKEN)).thenReturn(Single.just(jwt));
        when(clientService.findByDomainAndClientId(jwt.getDomain(), jwt.getAud())).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(token, client, REFRESH_TOKEN)).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(jwt.getJti())).thenReturn(Maybe.just(refreshToken));

        TestObserver testObserver = introspectionTokenService.introspect(token, false).test();
        testObserver.assertError(InvalidTokenException.class);
        verify(refreshTokenRepository, times(1)).findByToken(jwt.getJti());
    }
}
