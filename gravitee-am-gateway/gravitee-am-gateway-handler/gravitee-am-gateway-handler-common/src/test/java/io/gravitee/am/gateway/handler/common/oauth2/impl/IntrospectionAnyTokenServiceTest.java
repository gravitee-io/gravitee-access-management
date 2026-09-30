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
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionResult;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.am.repository.oauth2.model.Token;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.ACCESS_TOKEN;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.REFRESH_TOKEN;
import static io.gravitee.am.gateway.handler.common.oauth2.impl.BaseIntrospectionTokenService.LEGACY_RFC8707_ENABLED;
import static io.gravitee.am.gateway.handler.common.oauth2.impl.BaseIntrospectionTokenService.OFFLINE_VERIFICATION_TIMER_SECONDS_KEY;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IntrospectionAnyTokenServiceTest {

    private static final String TOKEN = "token";

    @Mock
    private JWTService jwtService;

    @Mock
    private ClientLookupService clientLookupService;

    @Mock
    private ProtectedResourceManager protectedResourceManager;

    @Mock
    private Environment environment;

    @Mock
    private TokenRepository tokenRepository;

    private IntrospectionAnyTokenService introspectionTokenService;

    @Before
    public void setUp() {
        when(environment.getProperty(LEGACY_RFC8707_ENABLED, Boolean.class, true)).thenReturn(false);
        when(environment.getProperty(OFFLINE_VERIFICATION_TIMER_SECONDS_KEY, Integer.class, 10)).thenReturn(10);
        introspectionTokenService = new IntrospectionAnyTokenService(jwtService, clientLookupService, protectedResourceManager, environment, tokenRepository);
    }

    @Test
    public void shouldResolveStoredRefreshTokenIntrospectedAsAccessToken() {
        JWT jwt = storedJwt();
        mockValidSignature(jwt, ACCESS_TOKEN);
        when(tokenRepository.findByJti("jti")).thenReturn(Maybe.just(stored(new RefreshToken())));

        TestObserver<IntrospectionResult> observer = introspectionTokenService.introspect(TOKEN, ACCESS_TOKEN, null).test();

        observer.assertValue(result -> result.jwt() == jwt
                && result.tokenType() == REFRESH_TOKEN
                && "stored-client".equals(result.clientId()));
        verify(jwtService, times(1)).decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Maybe<String>>any(), eq(ACCESS_TOKEN));
        verify(clientLookupService, times(1)).findByDomainAndClientId("domain", "client");
        verify(tokenRepository, times(1)).findByJti("jti");
    }

    @Test
    public void shouldResolveStoredAccessTokenIntrospectedAsRefreshToken() {
        JWT jwt = storedJwt();
        mockValidSignature(jwt, REFRESH_TOKEN);
        when(tokenRepository.findByJti("jti")).thenReturn(Maybe.just(stored(new AccessToken())));

        TestObserver<IntrospectionResult> observer = introspectionTokenService.introspect(TOKEN, REFRESH_TOKEN, null).test();

        observer.assertValue(result -> result.tokenType() == ACCESS_TOKEN);
        verify(tokenRepository, times(1)).findByJti("jti");
    }

    @Test
    public void shouldKeepExpectedTypeWhenTokenIsTooRecentToBeStored() {
        JWT jwt = storedJwt();
        jwt.setIat(Instant.now().getEpochSecond());
        mockValidSignature(jwt, REFRESH_TOKEN);

        TestObserver<IntrospectionResult> observer = introspectionTokenService.introspect(TOKEN, REFRESH_TOKEN, null).test();

        observer.assertValue(result -> result.tokenType() == REFRESH_TOKEN && result.clientId() == null);
        verify(tokenRepository, never()).findByJti(anyString());
    }

    @Test
    public void shouldRejectTokenNotFoundInDatabase() {
        JWT jwt = storedJwt();
        mockValidSignature(jwt, ACCESS_TOKEN);
        when(tokenRepository.findByJti("jti")).thenReturn(Maybe.empty());

        TestObserver<IntrospectionResult> observer = introspectionTokenService.introspect(TOKEN, ACCESS_TOKEN, null).test();

        observer.assertError(InvalidTokenException.class);
        verify(tokenRepository, times(1)).findByJti("jti");
    }

    @Test
    public void shouldRejectExpiredStoredToken() {
        JWT jwt = storedJwt();
        mockValidSignature(jwt, ACCESS_TOKEN);
        Token expired = stored(new AccessToken());
        expired.setExpireAt(new Date(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli()));
        when(tokenRepository.findByJti("jti")).thenReturn(Maybe.just(expired));

        TestObserver<IntrospectionResult> observer = introspectionTokenService.introspect(TOKEN, ACCESS_TOKEN, null).test();

        observer.assertError(InvalidTokenException.class);
    }

    @Test
    public void shouldNotLookUpTokenWhenSignatureIsInvalid() {
        JWT jwt = storedJwt();
        when(jwtService.decode(TOKEN, ACCESS_TOKEN)).thenReturn(Single.just(jwt));
        when(clientLookupService.findByDomainAndClientId("domain", "client")).thenReturn(Maybe.just(client()));
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Maybe<String>>any(), eq(ACCESS_TOKEN))).thenReturn(Single.error(new JWTException("invalid signature")));

        TestObserver<IntrospectionResult> observer = introspectionTokenService.introspect(TOKEN, ACCESS_TOKEN, null).test();

        observer.assertError(InvalidTokenException.class);
        verify(tokenRepository, never()).findByJti(anyString());
    }

    private void mockValidSignature(JWT jwt, JWTService.TokenType expectedType) {
        when(jwtService.decode(TOKEN, expectedType)).thenReturn(Single.just(jwt));
        when(clientLookupService.findByDomainAndClientId("domain", "client")).thenReturn(Maybe.just(client()));
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Maybe<String>>any(), eq(expectedType))).thenReturn(Single.just(jwt));
    }

    private static JWT storedJwt() {
        JWT jwt = new JWT();
        jwt.setJti("jti");
        jwt.setDomain("domain");
        jwt.setAud("client");
        jwt.setIat(Instant.now().minus(1, ChronoUnit.DAYS).getEpochSecond());
        return jwt;
    }

    private static Client client() {
        Client client = new Client();
        client.setClientId("client");
        client.setCertificate("cert-id");
        return client;
    }

    private static Token stored(Token token) {
        token.setToken("jti");
        token.setClient("stored-client");
        token.setExpireAt(new Date(Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli()));
        return token;
    }
}
