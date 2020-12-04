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
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.TokenServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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

    @Mock
    private ExecutionContextFactory executionContextFactory;

    @Mock
    private TokenManager tokenManager;

    @Test
    public void shouldCreate() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        Client client = new Client();
        client.setClientId("my-client-id");

        ExecutionContext executionContext = mock(ExecutionContext.class);

        when(jwtService.encode(any(), any(Client.class))).thenReturn(Single.just(""));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("token-id")));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doNothing().when(tokenManager).storeAccessToken(any());
        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
    }

    @Test
    public void shouldCreateWithPermissions() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setPermissions(Arrays.asList(new PermissionRequest().setResourceId("rs_one")));

        Client client = new Client();
        client.setClientId("my-client-id");

        ExecutionContext executionContext = mock(ExecutionContext.class);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        when(jwtService.encode(jwtCaptor.capture(), any(Client.class))).thenReturn(Single.just(""));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("token-id")));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doNothing().when(tokenManager).storeAccessToken(any());
        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        JWT jwt = jwtCaptor.getValue();
        assertTrue(jwt!=null && jwt.get("permissions")!=null);
        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
    }

    @Test
    public void shouldCreateWithCustomClaims() {
        OAuth2Request oAuth2Request = new OAuth2Request();

        TokenClaim customClaim = new TokenClaim();
        customClaim.setTokenType(TokenTypeHint.ACCESS_TOKEN);
        customClaim.setClaimName("iss");
        customClaim.setClaimValue("https://custom-iss");

        TokenClaim customClaim2 = new TokenClaim();
        customClaim2.setTokenType(TokenTypeHint.ACCESS_TOKEN);
        customClaim2.setClaimName("aud");
        customClaim2.setClaimValue("my-api");

        Client client = new Client();
        client.setClientId("my-client-id");
        client.setTokenCustomClaims(Arrays.asList(customClaim, customClaim2));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        TemplateEngine templateEngine = mock(TemplateEngine.class);
        when(templateEngine.getValue("https://custom-iss", Object.class)).thenReturn("https://custom-iss");
        when(templateEngine.getValue("my-api", Object.class)).thenReturn("my-api");
        when(executionContext.getTemplateEngine()).thenReturn(templateEngine);

        ArgumentCaptor<JWT> jwtCaptor = ArgumentCaptor.forClass(JWT.class);
        when(jwtService.encode(jwtCaptor.capture(), any(Client.class))).thenReturn(Single.just(""));
        when(tokenEnhancer.enhance(any(), any(), any(), any(), any())).thenReturn(Single.just(new AccessToken("token-id")));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        doNothing().when(tokenManager).storeAccessToken(any());
        TestObserver<Token> testObserver = tokenService.create(oAuth2Request, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        JWT jwt = jwtCaptor.getValue();
        assertNotNull(jwt);
        assertTrue(jwt.get("iss") != null && "https://custom-iss".equals(jwt.get("iss")));
        assertTrue(jwt.get("aud") != null && "my-api".equals(jwt.get("aud")));
        verify(tokenManager, times(1)).storeAccessToken(any());
        verify(accessTokenRepository, never()).delete(anyString());
        verify(refreshTokenRepository, never()).delete(anyString());
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

        when(jwtService.decodeAndVerify(any(), any(Client.class))).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));
        when(refreshTokenRepository.delete(anyString())).thenReturn(Completable.complete());

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, times(1)).delete(anyString());
    }

    @Test
    public void shouldRefreshWithPermissions() {
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
        jwt.put("permissions", Arrays.asList(new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("A"))));

        when(jwtService.decodeAndVerify(any(), any(Client.class))).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));
        when(refreshTokenRepository.delete(anyString())).thenReturn(Completable.complete());

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        //Check permissions are well available into the refresh_token object.
        testObserver.assertValue(token1 -> token1.getAdditionalInformation().get("permissions")!=null);
        //Check TokenRequest permissions field is well filled (will be used to propagate the permission into the final access_token)
        List<PermissionRequest> permissions = tokenRequest.getPermissions();
        assertNotNull(permissions);
        assertTrue("one".equals(permissions.get(0).getResourceId()) && "A".equals(permissions.get(0).getResourceScopes().get(0)));
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

        when(jwtService.decodeAndVerify(eq("encoded"), any(Client.class))).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.empty());

        TestObserver<Token> testObserver = tokenService.refresh("encoded", tokenRequest, client).test();
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

        when(jwtService.decodeAndVerify(eq(refreshToken.getToken()), any(Client.class))).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest, client).test();
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

        when(jwtService.decodeAndVerify(any(), any(Client.class))).thenReturn(Single.just(jwt));
        when(refreshTokenRepository.findByToken(any())).thenReturn(Maybe.just(refreshToken));

        TestObserver<Token> testObserver = tokenService.refresh(refreshToken.getToken(), tokenRequest, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidGrantException.class);

        verify(refreshTokenRepository, times(1)).findByToken(any());
        verify(refreshTokenRepository, never()).delete(anyString());
        verify(accessTokenRepository, never()).create(any());
    }
}
